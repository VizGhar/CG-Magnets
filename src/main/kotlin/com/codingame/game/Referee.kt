package com.codingame.game

import com.codingame.gameengine.core.AbstractPlayer
import com.codingame.gameengine.core.AbstractReferee
import com.codingame.gameengine.core.SoloGameManager
import com.codingame.gameengine.module.entities.*
import com.codingame.gameengine.module.toggle.ToggleModule
import com.codingame.gameengine.module.tooltip.TooltipModule
import com.google.inject.Inject
import kotlin.random.Random

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
    @Inject private lateinit var graphicEntityModule: GraphicEntityModule
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

    private fun initBoard() {
        graphicEntityModule.createSprite().setImage("background.jpg")
    }
}