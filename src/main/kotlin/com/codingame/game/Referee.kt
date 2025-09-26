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
    val topMarkers: List<Int>,
    val leftMarkers: List<Int>,
    val bottomMarkers: List<Int>,
    val rightMarkers: List<Int>,
    val plan: List<String>
)

class Referee : AbstractReferee() {

    @Inject private lateinit var gameManager: SoloGameManager<Player>
    @Inject private lateinit var graphic: GraphicEntityModule
    @Inject private lateinit var toggleModule: ToggleModule
    @Inject private lateinit var tooltips: TooltipModule

    private lateinit var testCase: TestCase
    private lateinit var playerSolution: List<String>

    override fun init() {
        gameManager.firstTurnMaxTime = 5000
        gameManager.turnMaxTime = 50
        gameManager.maxTurns = 2
        gameManager.frameDuration = 1000

        testCase = TestCase(
            width = gameManager.testCaseInput[0].toInt(),
            height = gameManager.testCaseInput[1].toInt(),
            topMarkers = gameManager.testCaseInput[2].map { it.digitToIntOrNull() ?: -1 },
            leftMarkers = gameManager.testCaseInput[3].map { it.digitToIntOrNull() ?: -1 },
            bottomMarkers = gameManager.testCaseInput[4].map { it.digitToIntOrNull() ?: -1 },
            rightMarkers = gameManager.testCaseInput[5].map { it.digitToIntOrNull() ?: -1 },
            plan = gameManager.testCaseInput.drop(6)
        )
        initBoard()
    }

    override fun gameTurn(turn: Int) {
        if (turn == 1) {
            for (line in gameManager.testCaseInput) {
                gameManager.player.sendInputLine(line.trim())
            }
        }

        try {
            // execution
            gameManager.player.execute()

            gameManager.player.outputs

            playerSolution = gameManager.player.outputs

        } catch (_: AbstractPlayer.TimeoutException) {
            gameManager.loseGame("Timeout!")
        }
    }


    private val cellSize = 150.0
    val scaling = mapOf(9 to 83 / cellSize, 7 to 107 / cellSize, 5 to 150.0 / cellSize)
    val scale by lazy { scaling[testCase.height] ?: throw IllegalStateException() }
    val magnetsPositive = mutableMapOf<Pair<Int, Int>, Sprite>()
    val magnetsNegative = mutableMapOf<Pair<Int, Int>, Sprite>()

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
                        cells.add(graphic.createSprite().setImage("magnet.png").setAnchor(0.5).setX((scaledCellSize * (x + 0.5)).toInt()).setY((scaledCellSize * (y + 1)).toInt()).setScale(0.2).setAlpha(0.0).also { magnetsPositive[x to y] = it })
                        cells.add(graphic.createSprite().setImage("magnet.png").setAnchor(0.5).setX((scaledCellSize * (x + 0.5)).toInt()).setY((scaledCellSize * (y + 1)).toInt()).setScale(0.2).setAlpha(0.0).setRotation(Math.toRadians(180.0)).also { magnetsNegative[x to y] = it })
                    }
                    targetH -> {
                        cells.add(graphic.createSprite().setImage("magnet_frame.png").setX((scaledCellSize * (x + 2)).toInt()).setY((scaledCellSize * y).toInt()).setScale(scale).setRotation(Math.toRadians(90.0)))
                        cells.add(graphic.createSprite().setImage("magnet.png").setAnchor(0.5).setX((scaledCellSize * (x + 1)).toInt()).setY((scaledCellSize * (y + 0.5)).toInt()).setScale(0.2).setAlpha(0.0).setRotation(Math.toRadians(270.0)).also { magnetsPositive[x to y] = it })
                        cells.add(graphic.createSprite().setImage("magnet.png").setAnchor(0.5).setX((scaledCellSize * (x + 1)).toInt()).setY((scaledCellSize * (y + 0.5)).toInt()).setScale(0.2).setAlpha(0.0).setRotation(Math.toRadians(90.0)).also { magnetsNegative[x to y] = it })
                    }
                }
            }
        }

        testCase.topMarkers.forEachIndexed { i, n -> if (n != -1) cells.add(graphic.createSprite().setImage("$n.png").setAnchorY(1.0).setAnchorX(0.5).setX((scaledCellSize * (i + 0.5)).toInt()).setY(-20)) }
        testCase.bottomMarkers.forEachIndexed { i, n -> if (n != -1)cells.add(graphic.createSprite().setImage("$n.png").setAnchorY(0.0).setAnchorX(0.5).setX((scaledCellSize * (i + 0.5)).toInt()).setY((cellsHeight + 20).toInt())) }
        testCase.leftMarkers.forEachIndexed { i, n -> if (n != -1) cells.add(graphic.createSprite().setImage("$n.png").setAnchorY(0.5).setAnchorX(1.0).setX(-20).setY((scaledCellSize * (i + 0.5)).toInt())) }
        testCase.rightMarkers.forEachIndexed { i, n -> if (n != -1) cells.add(graphic.createSprite().setImage("$n.png").setAnchorY(0.5).setAnchorX(0.0).setX((cellsWidth + 20).toInt()).setY((scaledCellSize * (i + 0.5)).toInt())) }
        cells.add(graphic.createSprite().setImage("+.png").setAnchorY(1.0).setAnchorX(1.0).setX(-20).setY(-20))
        cells.add(graphic.createSprite().setImage("-.png").setAnchorY(0.0).setAnchorX(0.0).setX((cellsWidth + 20).toInt()).setY((cellsHeight + 20).toInt()))
        board.add(cells)
    }
}