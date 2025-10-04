package com.codingame.game

import com.codingame.gameengine.core.AbstractPlayer
import com.codingame.gameengine.core.AbstractReferee
import com.codingame.gameengine.core.SoloGameManager
import com.codingame.gameengine.module.entities.*
import com.codingame.gameengine.module.toggle.ToggleModule
import com.codingame.gameengine.module.tooltip.TooltipModule
import com.google.inject.Inject

private data class TestCase(
    val width: Int,
    val height: Int,
    val leftMarkers: List<Int>,
    val topMarkers: List<Int>,
    val rightMarkers: List<Int>,
    val bottomMarkers: List<Int>,
    val plan: List<String>
)

class Referee : AbstractReferee() {

    @Inject private lateinit var gameManager: SoloGameManager<Player>
    @Inject private lateinit var graphic: GraphicEntityModule
    @Inject private lateinit var toggleModule: ToggleModule
    @Inject private lateinit var tooltips: TooltipModule

    private lateinit var testCase: TestCase

    override fun init() {
        gameManager.firstTurnMaxTime = 5000
        gameManager.turnMaxTime = 50
        gameManager.frameDuration = 1000

        testCase = TestCase(
            width = gameManager.testCaseInput[0].toInt(),
            height = gameManager.testCaseInput[1].toInt(),
            leftMarkers = gameManager.testCaseInput[2].split(" ").map { it.toInt() },
            topMarkers = gameManager.testCaseInput[3].split(" ").map { it.toInt() },
            rightMarkers = gameManager.testCaseInput[4].split(" ").map { it.toInt() },
            bottomMarkers = gameManager.testCaseInput[5].split(" ").map { it.toInt() },
            plan = gameManager.testCaseInput.drop(6)
        )
        initBoard()
        gameManager.maxTurns = (testCase.width * testCase.height) / 2
    }

    val playedCells = mutableSetOf<Char>()
    override fun gameTurn(turn: Int) {
        if (turn == 1) {
            for (line in gameManager.testCaseInput) {
                gameManager.player.sendInputLine(line.trim())
            }
        } else {
            for (y in 0..<testCase.height) {
                gameManager.player.sendInputLine((0..<testCase.width).joinToString("") { x ->
                    gamePlan[y][x].takeIf { it != '.' }?.toString() ?: testCase.plan[y][x].toString()
                })
            }
        }

        try {
            // execution
            gameManager.player.execute()

            if (gameManager.player.outputs[0].isEmpty()) { gameManager.loseGame("Empty output; space separated 'x', 'y' and '+/-/x' character expected."); return }
            val outputs = gameManager.player.outputs[0].split(" ")
            if (outputs.size != 3) { gameManager.loseGame("Space separated 'x', 'y' and '+/-/x' symbol expected."); return }
            val (xs, ys, symbol) = gameManager.player.outputs[0].split(" ")
            val x = xs.toIntOrNull() ?: run { gameManager.loseGame("Invalid output. X coordinate should be integer."); return }
            val y = ys.toIntOrNull() ?: run { gameManager.loseGame("Invalid output. Y coordinate should be integer."); return }
            if (symbol.length != 1 && symbol[0] !in listOf('+', '-', 'x')) { gameManager.loseGame("Invalid output. Symbol should be one of +/-/x."); return }
            val s = symbol[0]
            if (x !in 0..<testCase.width || y !in 0..<testCase.height) { gameManager.loseGame("Out of bounds."); return }
            if (testCase.plan[y][x] in playedCells) { gameManager.loseGame("Position [${x}, ${y}] already taken."); return }

            val errors = checkErrors(x, y, s)

            when (s) {
                '+' -> magnetsPositive[x to y]?.setAlpha(1.0)?.setScale(scale)
                '-' -> magnetsNegative[x to y]?.setAlpha(1.0)?.setScale(scale)
                'x' -> magnetsNeutral[x to y]?.setAlpha(1.0)?.setScale(scale)
            }

            playedCells += testCase.plan[y][x]
            when(testCase.plan[y][x]) {
                runCatching { testCase.plan[y][x - 1] }.getOrNull() -> playedCells += testCase.plan[y][x - 1]
                runCatching { testCase.plan[y][x + 1] }.getOrNull() -> playedCells += testCase.plan[y][x + 1]
                runCatching { testCase.plan[y - 1][x] }.getOrNull() -> playedCells += testCase.plan[y - 1][x]
                runCatching { testCase.plan[y + 1][x] }.getOrNull() -> playedCells += testCase.plan[y + 1][x]
            }

            if (errors.isNotEmpty()) {
                visualizeErrors(errors)
                gameManager.loseGame("You broke the rules!\n${errors.joinToString("\n") { it.text }}")
                return
            }
        } catch (_: AbstractPlayer.TimeoutException) {
            gameManager.loseGame("Timeout!")
        }

        if (win()) {
            gameManager.winGame()
        }
    }

    private fun visualizeErrors(errors: List<Error>) {
        for (error in errors) {
            when(error) {
                is Error.BottomError -> bottomCircles[error.column].setAlpha(1.0).also { tooltips.setTooltipText(it, error.text) }
                is Error.LeftError -> leftCircles[error.row].setAlpha(1.0).also { tooltips.setTooltipText(it, error.text) }
                is Error.Poles -> intersections[Tuple4(error.x1, error.y1, error.x2, error.y2)]?.setAlpha(1.0)?.also { tooltips.setTooltipText(it, error.text) }
                is Error.RightError -> rightCircles[error.row].setAlpha(1.0).also { tooltips.setTooltipText(it, error.text) }
                is Error.TopError -> topCircles[error.column].setAlpha(1.0).also { tooltips.setTooltipText(it, error.text) }
            }
        }
    }

    val gamePlan by lazy { Array(testCase.height) { Array(testCase.width) { '.' } } }

    sealed class Error(val text: String) {
        data class TopError(val column: Int): Error("Too many '+' in column $column")
        data class LeftError(val row: Int): Error("Too many '+' in row $row")
        data class BottomError(val column: Int): Error("Too many '-' in column $column")
        data class RightError(val row: Int): Error("Too many '-' in row $row")
        data class Poles(val x1: Int, val y1: Int, val x2: Int, val y2: Int): Error("Neighboring cells with same polarity at [$x1, $y1] [$x2, $y2]")
    }

    fun checkErrors(x: Int, y: Int, s: Char): List<Error> {
        if (s == 'x') return emptyList()
        val errors = mutableListOf<Error>()
        val c = testCase.plan[y][x]
        val op = if (s == '+') '-' else '+'
        gamePlan[y][x] = s
        when (c) {
            runCatching { testCase.plan[y][x - 1] }.getOrNull() -> gamePlan[y][x - 1] = op
            runCatching { testCase.plan[y][x + 1] }.getOrNull() -> gamePlan[y][x + 1] = op
            runCatching { testCase.plan[y - 1][x] }.getOrNull() -> gamePlan[y - 1][x] = op
            runCatching { testCase.plan[y + 1][x] }.getOrNull() -> gamePlan[y + 1][x] = op
        }
        for (x in 0..<testCase.width) {
            if (testCase.topMarkers[x] != -1 && gamePlan.map { it[x] }.count { it == '+' } > testCase.topMarkers[x]) errors += Error.TopError(x)
            if (testCase.bottomMarkers[x] != -1 && gamePlan.map { it[x] }.count { it == '-' } > testCase.bottomMarkers[x]) errors += Error.BottomError(x)
        }
        for (y in 0..<testCase.height) {
            if (testCase.leftMarkers[y] != -1 && gamePlan[y].count { it == '+' } > testCase.leftMarkers[y]) errors += Error.LeftError(y)
            if (testCase.rightMarkers[y] != -1 && gamePlan[y].count { it == '-' } > testCase.rightMarkers[y]) errors += Error.RightError(y)
        }
        for (x in 1..<testCase.width) { for (y in 0..<testCase.height) {
                if (gamePlan[y][x] in listOf('+', '-') && gamePlan[y][x] == gamePlan[y][x - 1]) errors += Error.Poles(x - 1, y, x, y)
        }}
        for (x in 0..<testCase.width) { for (y in 1..<testCase.height) {
                if (gamePlan[y][x] in listOf('+', '-') && gamePlan[y][x] == gamePlan[y - 1][x]) errors += Error.Poles(x, y - 1, x, y)
        }}
        return errors
    }

    fun win(): Boolean {
        return gamePlan.all { it.none { it == '.' } }
    }

    private val cellSize = 150.0
    val scaling = mapOf(9 to 83 / cellSize, 7 to 107 / cellSize, 5 to 150.0 / cellSize)
    val scale by lazy { scaling[testCase.height] ?: throw IllegalStateException() }
    val magnetsPositive = mutableMapOf<Pair<Int, Int>, Sprite>()
    val magnetsNegative = mutableMapOf<Pair<Int, Int>, Sprite>()
    val magnetsNeutral = mutableMapOf<Pair<Int, Int>, Sprite>()

    val topCircles = mutableListOf<Circle>()
    val bottomCircles = mutableListOf<Circle>()
    val leftCircles = mutableListOf<Circle>()
    val rightCircles = mutableListOf<Circle>()

    data class Tuple4(val a: Int, val b: Int, val c: Int, val d: Int)
    val intersections = mutableMapOf<Tuple4, Sprite>()

    private fun initBoard() {
        graphic.createSprite().setImage("background.jpg")

        val scaledCellSize = cellSize * scale
        val cellsWidth = testCase.width * scaledCellSize
        val cellsHeight = testCase.height * scaledCellSize
        val board = graphic.createGroup()
            .setX((graphic.world.width - cellsWidth.toInt() - 200) / 2)
            .setY((graphic.world.height - cellsHeight.toInt() - 200) / 2)
        val cells = graphic.createGroup().setX(100).setY(100)

        for (y in 0..<testCase.height) {
            for (x in 0..<testCase.width) {
                val actual = testCase.plan[y][x]
                val targetV = runCatching { testCase.plan[y + 1][x] }.getOrNull()
                val targetH = runCatching { testCase.plan[y][x + 1] }.getOrNull()
                when (actual) {
                    targetV -> {
                        cells.add(graphic.createSprite().setImage("magnet_frame.png").setX((scaledCellSize * x).toInt()).setY((scaledCellSize * y).toInt()).setScale(scale))
                        cells.add(graphic.createSprite().setImage("magnet.png").setAnchor(0.5).setX((scaledCellSize * (x + 0.5)).toInt()).setY((scaledCellSize * (y + 1)).toInt()).setScale(0.2).setAlpha(0.0).also { magnetsPositive[x to y] = it; magnetsNegative[x to y + 1] = it })
                        cells.add(graphic.createSprite().setImage("magnet.png").setAnchor(0.5).setX((scaledCellSize * (x + 0.5)).toInt()).setY((scaledCellSize * (y + 1)).toInt()).setScale(0.2).setAlpha(0.0).setRotation(Math.toRadians(180.0)).also { magnetsNegative[x to y] = it; magnetsPositive[x to y + 1] = it })
                        cells.add(graphic.createSprite().setImage("wood.png").setAnchor(0.5).setX((scaledCellSize * (x + 0.5)).toInt()).setY((scaledCellSize * (y + 1)).toInt()).setScale(0.2).setAlpha(0.0).also { magnetsNeutral[x to y] = it; magnetsNeutral[x to y + 1] = it })
                    }
                    targetH -> {
                        cells.add(graphic.createSprite().setImage("magnet_frame.png").setX((scaledCellSize * (x + 2)).toInt()).setY((scaledCellSize * y).toInt()).setScale(scale).setRotation(Math.toRadians(90.0)))
                        cells.add(graphic.createSprite().setImage("magnet.png").setAnchor(0.5).setX((scaledCellSize * (x + 1)).toInt()).setY((scaledCellSize * (y + 0.5)).toInt()).setScale(0.2).setAlpha(0.0).setRotation(Math.toRadians(270.0)).also { magnetsPositive[x to y] = it; magnetsNegative[x + 1 to y] = it })
                        cells.add(graphic.createSprite().setImage("magnet.png").setAnchor(0.5).setX((scaledCellSize * (x + 1)).toInt()).setY((scaledCellSize * (y + 0.5)).toInt()).setScale(0.2).setAlpha(0.0).setRotation(Math.toRadians(90.0)).also { magnetsNegative[x to y] = it; magnetsPositive[x + 1 to y] = it })
                        cells.add(graphic.createSprite().setImage("wood.png").setAnchor(0.5).setX((scaledCellSize * (x + 1)).toInt()).setY((scaledCellSize * (y + 0.5)).toInt()).setScale(0.2).setAlpha(0.0).setRotation(Math.toRadians(270.0)).also { magnetsNeutral[x to y] = it; magnetsNeutral[x + 1 to y] = it })
                    }
                }

                cells.add(
                    graphic.createRectangle().setX((scaledCellSize * x).toInt()).setY((scaledCellSize * y).toInt()).setWidth(scaledCellSize.toInt()).setHeight(scaledCellSize.toInt()).setFillColor(0xFFFFFF).setAlpha(0.5).setZIndex(1).also { toggleModule.displayOnToggleState(it, "coords", true) },
                    graphic.createText().setAnchor(0.5).setX((scaledCellSize * (x + 0.5)).toInt()).setY((scaledCellSize * (y + 0.5)).toInt()).setText("[$x, $y]").setFontSize((scaledCellSize / 3).toInt()).setFillColor(0x000000).setFontWeight(Text.FontWeight.BOLDER).setZIndex(2).also { toggleModule.displayOnToggleState(it, "coords", true) }
               )
            }
        }

        testCase.topMarkers.forEachIndexed { i, n -> if (n != -1) cells.add(graphic.createSprite().setImage("$n.png").setAnchorY(1.0).setAnchorX(0.5).setX((scaledCellSize * (i + 0.5)).toInt()).setY(-20).setTint(0xE93333)) }
        testCase.bottomMarkers.forEachIndexed { i, n -> if (n != -1) cells.add(graphic.createSprite().setImage("$n.png").setAnchorY(0.0).setAnchorX(0.5).setX((scaledCellSize * (i + 0.5)).toInt()).setY((cellsHeight + 20).toInt()).setTint(0x3453B9)) }
        testCase.leftMarkers.forEachIndexed { i, n -> if (n != -1) cells.add(graphic.createSprite().setImage("$n.png").setAnchorY(0.5).setAnchorX(1.0).setX(-20).setY((scaledCellSize * (i + 0.5)).toInt()).setTint(0xE93333)) }
        testCase.rightMarkers.forEachIndexed { i, n -> if (n != -1) cells.add(graphic.createSprite().setImage("$n.png").setAnchorY(0.5).setAnchorX(0.0).setX((cellsWidth + 20).toInt()).setY((scaledCellSize * (i + 0.5)).toInt()).setTint(0x3453B9)) }
        for (column in 0..<testCase.width) {
            topCircles += graphic.createCircle().setRadius(40).setX((scaledCellSize * (column + 0.5)).toInt()).setY(-33).setFillAlpha(0.0).setLineColor(0xE93333).setLineWidth(8.0).setAlpha(0.0)
            bottomCircles += graphic.createCircle().setRadius(40).setX((scaledCellSize * (column + 0.5)).toInt()).setY((cellsHeight + 33).toInt()).setFillAlpha(0.0).setLineColor(0xE93333).setLineWidth(8.0).setAlpha(0.0)
            cells.add(topCircles.last(), bottomCircles.last())
        }
        for (row in 0..<testCase.height) {
            leftCircles += graphic.createCircle().setRadius(40).setX(-28).setY((scaledCellSize * (row + 0.5)).toInt()).setFillAlpha(0.0).setLineColor(0xE93333).setLineWidth(8.0).setAlpha(0.0)
            rightCircles += graphic.createCircle().setRadius(40).setX((cellsWidth + 28).toInt()).setY((scaledCellSize * (row + 0.5)).toInt()).setFillAlpha(0.0).setLineColor(0xE93333).setLineWidth(8.0).setAlpha(0.0)
            cells.add(leftCircles.last(), rightCircles.last())
        }
        for (x in 1..<testCase.width) {
            for (y in 0..<testCase.height) {
                intersections += Tuple4(x - 1, y, x, y) to graphic.createSprite().setImage("bolt.png").setAnchor(0.5).setX((scaledCellSize * x).toInt()).setY((scaledCellSize * (y + 0.5)).toInt()).setScale(scale * 0.8).setAlpha(0.0)
            }
        }
        for (y in 1..<testCase.height) {
            for (x in 0..<testCase.width) {
                intersections += Tuple4(x, y - 1, x, y) to graphic.createSprite().setImage("bolt.png").setAnchor(0.5).setX((scaledCellSize * (x + 0.5)).toInt()).setY((scaledCellSize * y).toInt()).setScale(scale * 0.8).setAlpha(0.0)
            }
        }
        cells.add(*intersections.values.toTypedArray())
        cells.add(graphic.createSprite().setImage("+.png").setAnchorY(1.0).setAnchorX(1.0).setX(-20).setY(-20).setTint(0xE93333))
        cells.add(graphic.createSprite().setImage("-.png").setAnchorY(0.0).setAnchorX(0.0).setX((cellsWidth + 20).toInt()).setY((cellsHeight + 20).toInt()).setTint(0x3453B9))
        board.add(cells)
    }
}