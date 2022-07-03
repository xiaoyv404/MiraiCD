package win.rainchan.mirai.miraicd

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.registeredCommands
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.plugin.PluginManager.INSTANCE.load
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.info
import net.mamoe.yamlkt.Yaml
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.io.path.*


object MiraiCDMain : KotlinPlugin(
    JvmPluginDescription(
        id = "win.rainchan.mirai.miraicd",
        name = "MiraiCD",
        version = "0.1.0"
    ) {
        author("RainChan")

    }
), WebHookHandler {
    private lateinit var server: WebHookServer
    private val configMap = hashMapOf<String, DeployConfig>()
    private val runningCount = AtomicInteger(0)



    override fun onEnable() {
        logger.info { "Plugin loaded" }
        configFolder.mkdir()
        dataFolder.mkdir()
        loadConfig()
        PlConfig.reload()
        DeployCmd().register()
        server = WebHookServer(PlConfig.port, this)
        server.start()
//        val task = DeployTask(
//            dataFolderPath,
//            MiraiConsole.INSTANCE.rootPath,
//            "MiraiCD",
//            "git@github.com:mzdluo123/MiraiCD.git",
//            "master",
//            "buildPlugin"
//        )
//        startDeployTask(task)
    }

    override fun onTag(repo: String, tag: String) {
        logger.info("onTag ${repo} ${tag}")
        val i = configMap[repo] ?: return
        if (i.tag_regex.isEmpty()) {
            return
        }
        val regex = i.tag_regex.toRegex()
        if (regex.matches(tag)) {
            val task = i.toTask(dataFolderPath, MiraiConsole.INSTANCE.rootPath, repo)
            startDeployTask(task)
        }
    }

    override fun onPush(repo: String, branch: String) {
        logger.info("onPush ${repo} ${branch}")
        val i = configMap[repo] ?: return
        if (i.branch.isEmpty()) {
            return
        }
        if (branch == i.branch) {
            val task = i.toTask(dataFolderPath, MiraiConsole.INSTANCE.rootPath, repo)
            startDeployTask(task)
        }
    }

    private fun loadConfig() {
        val dir = configFolderPath.resolve("projects")
        if (dir.notExists()){
            dir.toFile().mkdir()
        }
        for (i in dir.listDirectoryEntries()) {
            val item = Yaml.decodeFromString(DeployConfig.serializer(), i.readText())
            configMap[i.name.split(".")[0]] = item
        }
    }

    private fun startDeployTask(task: DeployTask) {
        thread {
           try {
               runningCount.addAndGet(1)
               task.deploy()
               if (runningCount.decrementAndGet() == 0){
                   restart()
               }
           }catch (e:java.lang.Exception){
               logger.error(e)
           }
            runningCount.decrementAndGet()
        }
    }
    fun manualDeploy(){
        for (i in configMap.keys){
            val task = configMap[i] ?: continue
            startDeployTask(task.toTask(dataFolderPath, MiraiConsole.INSTANCE.rootPath, i))
        }
    }

    private fun restart(){
        runBlocking {
            CommandManager.INSTANCE.executeCommand(ConsoleCommandSender, PlainText("/stop"), false)
        }
    }
}

class DeployCmd: SimpleCommand(MiraiCDMain,"deploy", description = "手动部署") {
    @Handler
    suspend fun onCmd(){
        MiraiCDMain.manualDeploy()
    }
}