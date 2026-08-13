package com.qlm.zombie.ai

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.ai.companion.CompanionManager
import com.qlm.zombie.ai.task.TaskCatalogue
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class Player2APIService(
    private val port: Int = 18921
) {
    data class AIResponse(
        @get:JvmName("action") val action: String,
        val targetItem: String?,
        val targetCount: Int,
        val params: Map<String, Any>? = null
    )

    private var serverSocket: ServerSocket? = null
    private var running: Boolean = false
    private val executor = Executors.newCachedThreadPool()
    private val gson = Gson()

    companion object {
        private var instance: Player2APIService? = null

        fun getInstance(port: Int = 18921): Player2APIService {
            if (instance == null) {
                instance = Player2APIService(port)
            }
            return instance!!
        }

        @JvmStatic
        fun isPlayer2Available(): Boolean {
            return instance?.running == true
        }
    }

    fun start() {
        if (running) return
        try {
            serverSocket = ServerSocket(port)
            running = true
            QLMZombieMod.LOGGER.info("[Player2API] MCP API服务已启动: localhost:$port")
            executor.submit { acceptLoop() }
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.error("[Player2API] 无法启动API服务: ${e.message}")
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        executor.shutdownNow()
        QLMZombieMod.LOGGER.info("[Player2API] MCP API服务已停止")
    }

    private fun acceptLoop() {
        while (running) {
            try {
                val client = serverSocket?.accept() ?: break
                executor.submit { handleClient(client) }
            } catch (e: Exception) {
                if (running) {
                    QLMZombieMod.LOGGER.error("[Player2API] 连接错误: ${e.message}")
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.inputStream))
            val writer = OutputStreamWriter(client.outputStream)

            val line = reader.readLine()
            if (line == null) {
                client.close()
                return
            }

            val request = parseRequest(line)
            val response = processRequest(request)

            writer.write(gson.toJson(response))
            writer.flush()
        } catch (e: Exception) {
            QLMZombieMod.LOGGER.error("[Player2API] 客户端处理错误: ${e.message}")
        } finally {
            try {
                client.close()
            } catch (_: Exception) {}
        }
    }

    private fun parseRequest(line: String): Map<String, Any> {
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(line, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            mapOf("action" to "unknown", "params" to emptyMap<String, Any>())
        }
    }

    private fun processRequest(request: Map<String, Any>): Map<String, Any> {
        val action = request["action"] as? String ?: "unknown"
        val params = request["params"] as? Map<String, Any> ?: emptyMap()

        return when (action) {
            "status" -> handleStatus()
            "execute" -> handleExecute(params)
            "command" -> handleCommand(params)
            "companions" -> handleCompanions()
            "spawn" -> handleSpawn(params)
            "despawn" -> handleDespawn(params)
            "tame" -> handleTame(params)
            "tasks" -> handleListTasks()
            else -> mapOf("success" to false, "error" to "未知操作: $action")
        }
    }

    private fun handleStatus(): Map<String, Any> {
        return mapOf(
            "success" to true,
            "status" to "ok",
            "companions" to CompanionManager.getInstance().getAll().size,
            "serverTime" to System.currentTimeMillis()
        )
    }

    private fun handleExecute(params: Map<String, Any>): Map<String, Any> {
        val command = params["command"] as? String ?: return mapOf("success" to false, "error" to "缺少command参数")
        val playerName = params["player"] as? String ?: return mapOf("success" to false, "error" to "缺少player参数")

        val companion = CompanionManager.getInstance().getByName(playerName)
            ?: return mapOf("success" to false, "error" to "未找到伴侣: $playerName")

        val args = (params["args"] as? List<*>)?.map { it.toString() } ?: emptyList()
        val entity = companion.entity ?: return mapOf("success" to false, "error" to "伴侣实体不可用")
        val task = TaskCatalogue.getTask(command, entity, args)
            ?: return mapOf("success" to false, "error" to "无法创建任务: $command")

        companion.taskRunner.addTask(task)
        return mapOf("success" to true, "message" to "任务已提交: $command")
    }

    private fun handleCommand(params: Map<String, Any>): Map<String, Any> {
        val command = params["command"] as? String ?: return mapOf("success" to false, "error" to "缺少command参数")
        return mapOf("success" to true, "message" to "命令已接收: $command")
    }

    private fun handleCompanions(): Map<String, Any> {
        val list = CompanionManager.getInstance().getAll().map { info ->
            mapOf(
                "name" to info.name,
                "uuid" to info.uuid.toString(),
                "affinity" to info.affinity,
                "tamed" to info.isTamed
            )
        }
        return mapOf("success" to true, "companions" to list)
    }

    private fun handleSpawn(params: Map<String, Any>): Map<String, Any> {
        val playerName = params["owner"] as? String ?: return mapOf("success" to false, "error" to "缺少owner参数")
        val name = params["name"] as? String ?: "AI伴侣"

        return mapOf("success" to true, "message" to "伴侣生成请求已发送给 $playerName")
    }

    private fun handleDespawn(params: Map<String, Any>): Map<String, Any> {
        val name = params["name"] as? String ?: return mapOf("success" to false, "error" to "缺少name参数")
        val info = CompanionManager.getInstance().getByName(name)
            ?: return mapOf("success" to false, "error" to "未找到伴侣: $name")
        CompanionManager.getInstance().despawnCompanion(info.uuid)
        return mapOf("success" to true, "message" to "伴侣已移除: $name")
    }

    private fun handleTame(params: Map<String, Any>): Map<String, Any> {
        val name = params["name"] as? String ?: return mapOf("success" to false, "error" to "缺少name参数")
        val amount = (params["amount"] as? Number)?.toFloat() ?: 10f
        val info = CompanionManager.getInstance().getByName(name)
            ?: return mapOf("success" to false, "error" to "未找到伴侣: $name")
        val tamed = CompanionManager.getInstance().tame(info.uuid, amount)
        return mapOf("success" to true, "tamed" to tamed, "affinity" to info.affinity)
    }

    private fun handleListTasks(): Map<String, Any> {
        val commands = TaskCatalogue.getCommandHelp()
        return mapOf("success" to true, "tasks" to commands)
    }
}