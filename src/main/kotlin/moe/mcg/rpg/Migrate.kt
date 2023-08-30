package moe.mcg.rpg

import com.handy.playertitle.api.PlayerTitleApi
import com.praya.dreamfish.DreamFish
import com.praya.dreamfish.player.PlayerFishing
import com.sucy.skill.SkillAPI
import com.sucy.skill.api.classes.RPGClass
import com.sucy.skill.api.util.FlagManager
import moe.gensoukyo.lib.server.bukkit
import moe.gensoukyo.lib.util.cnpc.apiext.noppes.npcs.api.entity.uuidObject
import net.milkbowl.vault.economy.Economy
import noppes.npcs.api.NpcAPI
import noppes.npcs.api.entity.IPlayer
import noppes.npcs.api.event.CustomGuiEvent
import noppes.npcs.api.event.ItemEvent
import noppes.npcs.api.gui.IButton
import noppes.npcs.api.gui.ICustomGui
import noppes.npcs.api.gui.ILabel
import noppes.npcs.api.gui.ITextField
import noppes.npcs.controllers.data.PlayerData
import org.bukkit.Bukkit
import org.jetbrains.kotlin.backend.common.push

/**
 * 账号转移工具
 * at -287 73 1341
 * @author xiaoyv_404
 * @author uncle__gen
 */
class Migrate {
    data class IPlayerClass(
        val level: Int,
        val exp: Double,
        val data: RPGClass,
        val points: Int
    )

    companion object {
        var player: IPlayer<*>? = null
        var from: String? = null
        val titleList = mutableListOf<Long>()
        var money = 0.0
        var fishing: PlayerFishing? = null
        var dialogData: HashSet<Int>? = null
        var activeQuests: List<Int>? = null
        var finishedQuests: List<Int>? = null
        var factionData: HashMap<Int, Int>? = null
        var accounts: List<IPlayerClass>? = null

        private const val ID_FROM_NAME_TEXT_FIELD = 1
        private const val ID_TARGET_NAME_TEXT_FIELD = 5
        private const val ID_QUERY_BUTTON = 3
        private const val ID_INFO_LABLE = 4
        private const val ID_CONFIRM_BUTTON = 2
        private const val ID_CLEAR_BUTTON = 6
        private const val ID_DELETE_BUTTON = 7
    }

    private var confirmButtonClicks = 0
    private var deleteButtonClicks = 0
    private val ICustomGui.fromNameTextField get() = this.get<ITextField>(1)
    private val ICustomGui.queryButton get() = this.get<IButton>(ID_QUERY_BUTTON)
    private val ICustomGui.targetNameTextField get() = this.get<ITextField>(5)
    private val ICustomGui.confirmButton get() = this.get<IButton>(ID_CONFIRM_BUTTON)
    private val ICustomGui.infoLable get() = this.get<ILabel>(ID_INFO_LABLE)
    private val ICustomGui.deleteButton get() = this.get<IButton>(ID_DELETE_BUTTON)
    fun interact(e: ItemEvent.InteractEvent) {
        confirmButtonClicks = 0
        deleteButtonClicks = 0
        e.API.createCustomGui(1, 200, 200, false).apply {
            addTextField(ID_FROM_NAME_TEXT_FIELD, 0, 0, 100, 20).apply {
                text = from ?: "来源玩家名称"
            }
            addButton(ID_QUERY_BUTTON, "查询", 120, 0, 50, 20)
            addTextField(ID_TARGET_NAME_TEXT_FIELD, 0, 30, 100, 20).apply {
                text = "目标玩家名称"
            }
            addButton(ID_CONFIRM_BUTTON, "转移", 120, 30, 50, 20)
            addLabel(ID_INFO_LABLE, info, 200 - 20, 20, 200, 20)
            addButton(ID_CLEAR_BUTTON, "清除", 0, 60, 50, 20)
            addButton(ID_DELETE_BUTTON, "删除旧帐号", 0, 90, 50, 20)
            e.player.showCustomGui(this)
        }
    }

    @Suppress("unused")
    fun customGuiButton(e: CustomGuiEvent.ButtonEvent) {
        when (e.buttonId) {
            ID_CONFIRM_BUTTON -> {
                val confirmButton = e.gui.confirmButton
                val player = e.player.world.getPlayer(e.gui.targetNameTextField.text)
                if (player == null) {
                    confirmButton.label = "目标不存在"
                    e.gui.update(e.player)
                    confirmButtonClicks = 0
                    return
                }
                if (from == null) {
                    confirmButton.label = "来源数据为空"
                    e.gui.update(e.player)
                    confirmButtonClicks = 0
                    return
                }
                confirmButtonClicks++
                when (confirmButtonClicks) {
                    1 -> confirmButton.label = "确认吗"
                    2 -> {
                        setter(e.API, player)
                        confirmButton.label = "转移成功"
                    }

                    else -> confirmButton.label = "点那么多次是没用的！"
                }
                e.gui.updateComponent(confirmButton)
            }

            ID_QUERY_BUTTON -> {
                val player = e.player.world.getPlayer(e.gui.fromNameTextField.text)
                if (player == null) {
                    e.gui.queryButton.label = "未查询到"
                    e.gui.update(e.player)
                    return
                } else
                    e.gui.queryButton.label = "查询"
                getter(player)
                e.gui.infoLable.text = info
            }

            ID_CLEAR_BUTTON -> {
                clear()
                e.gui.infoLable.text = info
            }

            ID_DELETE_BUTTON -> {
                val deleteButton = e.gui.deleteButton
                if (player == null) {
                    deleteButton.label = "目标不存在"
                    e.gui.update(e.player)
                    deleteButtonClicks = 0
                    return
                }
                deleteButtonClicks++
                when (deleteButtonClicks) {
                    1 -> deleteButton.label = "确认吗"
                    2 -> {
                        deleter(e.API, player!!)
                        deleteButton.label = "删除成功"
                    }

                    else -> deleteButton.label = "点那么多次是没用的！"
                }
                e.gui.updateComponent(deleteButton)
            }
        }
        e.gui.update(e.player)
    }


    private fun clear() {
        player = null
        from = null
        titleList.clear()
        money = 0.0
        fishing = null
        dialogData = null
        activeQuests = null
        finishedQuests = null
        factionData = null
        accounts = null
    }

    private fun getter(player: IPlayer<*>) {
        Migrate.player = player
        from = player.name
        fishingLevelGetter(player)
        taskGetter(player)
        factionPointGetter(player)
        titleGetter(player)
        moneyGetter(player)
        accountGetter(player)
    }

    private fun setter(api: NpcAPI, player: IPlayer<*>) {
        luckPermsSetter(api, player)
        fishingLevelSetter(player)
        taskSetter(player)
        factionPointSetter(player)
        titleSetter(player)
        moneySetter(player)
        flagSetter(player)
        accountSetter(api, player)
    }

    private fun fishingLevelGetter(player: IPlayer<*>) {
        val dreamfish = Bukkit.getPluginManager().getPlugin("DreamFish") as DreamFish
        val manager = dreamfish.playerManager.playerFishingManager
        val olpl = Bukkit.getPlayer(player.uuidObject)
        fishing = manager.getPlayerFishing(olpl)
        player.message("钓鱼等级获取完毕")
    }

    private fun taskGetter(player: IPlayer<*>) {
        val playerData = PlayerData.get(player.mcEntity)
        dialogData = playerData.dialogData.dialogsRead
        player.message("对话记录完毕")

        activeQuests = player.activeQuests.map {
            it.id
        }
        finishedQuests = player.finishedQuests.map {
            it.id
        }

        player.message("任务记录完毕")
    }

    private fun factionPointGetter(player: IPlayer<*>) {
        val playerData = PlayerData.get(player.mcEntity)
        factionData = playerData.factionData.factionData
        player.message("阵营值获取完毕")
    }

    private fun titleGetter(player: IPlayer<*>) {
        val playerTitleApi = PlayerTitleApi.getInstance()
        val titleNum = playerTitleApi.getPlayerTitleNum(player.name)

        for (i in 1..500) {
            if (playerTitleApi.playerExistTitleId(player.name, i))
                titleList.push(i.toLong())
            if (titleList.size == titleNum)
                break
        }
        player.message("称号ID获取完成")
    }

    private fun moneyGetter(player: IPlayer<*>) {
        val provider = Bukkit.getServer().servicesManager.getRegistration(Economy::class.java).provider
        val olpl = Bukkit.getPlayer(player.uuidObject)
        money = provider.getBalance(olpl)
        player.message("Money获取完成")
    }

    private fun accountGetter(player: IPlayer<*>) {
        accounts = SkillAPI.getPlayerAccountData(player.bukkit).allData.map {
            it.value.classes.firstOrNull().let { playerClass ->
                if (playerClass != null)
                    IPlayerClass(playerClass.level, playerClass.exp, playerClass.data, playerClass.points)
                else
                    null
            }
        }.filterNotNull()
    }

    private fun luckPermsSetter(api: NpcAPI, player: IPlayer<*>) {
        api.executeCommand(player.world, "lp user $from clone ${player.name}")
    }

    private fun fishingLevelSetter(player: IPlayer<*>) {
        val dreamfish = Bukkit.getPluginManager().getPlugin("DreamFish") as DreamFish
        val manager = dreamfish.playerManager.playerFishingManager
        val olpl = Bukkit.getPlayer(player.uuidObject)
        val playerFishing = manager.getPlayerFishing(olpl) as PlayerFishing
        playerFishing.level = fishing?.level ?: 0
        playerFishing.exp = fishing?.exp ?: 0F
        player.message("钓鱼等级设置完毕")
    }

    private fun taskSetter(player: IPlayer<*>) {
        activeQuests?.forEach {
            player.startQuest(it)
        }
        finishedQuests?.forEach {
            player.finishQuest(it)
        }
        player.message("任务设置完毕")
        dialogData?.forEach {
            player.addDialog(it)
        }
        player.message("对话设置完毕")
    }

    private fun factionPointSetter(player: IPlayer<*>) {
        val playerData = PlayerData.get(player.mcEntity)
        playerData.factionData.factionData = factionData
        player.message("阵营值设置完毕")
    }

    private fun titleSetter(player: IPlayer<*>) {
        val playerTitleApi = PlayerTitleApi.getInstance()
        titleList.forEach {
            playerTitleApi[player.name, it] = 0
        }
        player.message("称号设置完毕")
    }

    private fun moneySetter(player: IPlayer<*>) {
        val provider = Bukkit.getServer().servicesManager.getRegistration(Economy::class.java).provider
        val olpl = Bukkit.getPlayer(player.uuidObject)
        provider.depositPlayer(olpl, money)
        player.message("Money设置完成")
    }

    private fun flagSetter(player: IPlayer<*>) {
        FlagManager.addFlag(player.bukkit, "777", 99)
    }

    private fun accountSetter(api: NpcAPI, player: IPlayer<*>) {
        val newPlayerAccounts = SkillAPI.getPlayerAccountData(player.bukkit)
        accounts?.forEachIndexed { index, iPlayerClasses ->
            api.executeCommand(player.world, "class forceaccount ${player.name} ${index + 1}")
            api.executeCommand(player.world, "class forceprofess ${player.name} 初心者")
            newPlayerAccounts.getData(index + 1).classes.first().let {
                it.level = iPlayerClasses.level
                it.exp = iPlayerClasses.exp
                it.setClassData(iPlayerClasses.data)
                it.points = iPlayerClasses.points
            }
        }
    }

    private val info
        get() = """from: $from
钓鱼等级: ${fishing?.level}
钓鱼经验: ${fishing?.exp}
对话数量: ${dialogData?.size}
进行中的任务: ${activeQuests?.size}
已结束的任务: ${finishedQuests?.size}
阵营值数量: ${factionData?.size}
称号数量: ${titleList.size}
金钱数量: $money
职业帐号数量: ${accounts?.size}
                """.trimMargin()

    private fun deleter(api: NpcAPI, player: IPlayer<*>) {
        player.message("test")
        luckPermsDeleter(api, player)
        fishingLevelDeleter(player)
        cNpcDeleter(player)
        titleDeleter(player)
        moneyDeleter(player)
        flagDeleter(player)
        sapiAccountDeleter(api, player)
        accountDeleter(api, player)
        player.message("删除成功")
    }

    private fun luckPermsDeleter(api: NpcAPI, player: IPlayer<*>) {
        api.executeCommand(player.world, "lp ${player.name} clear")
    }

    private fun fishingLevelDeleter(player: IPlayer<*>) {
        val dreamfish = Bukkit.getPluginManager().getPlugin("DreamFish") as DreamFish
        val manager = dreamfish.playerManager.playerFishingManager
        val olpl = Bukkit.getPlayer(player.uuidObject)
        manager.getPlayerFishing(olpl).apply {
            level = 0
            exp = 0f
        }
        manager.removeFromCache(olpl)
    }

    private fun cNpcDeleter(player: IPlayer<*>) {
        player.clearData()
    }

    private fun titleDeleter(player: IPlayer<*>) {
        val playerTitleApi = PlayerTitleApi.getInstance()
        var titleNum = playerTitleApi.getPlayerTitleNum(player.name)
        for (i in 1..500) {
            if (playerTitleApi.playerExistTitleId(player.name, i)) {
                playerTitleApi.removePlayerTitle(player.name, i.toLong())
                titleNum--
            }
            if (titleNum == 0)
                break
        }
    }

    private fun moneyDeleter(player: IPlayer<*>) {
        val olpl = Bukkit.getPlayer(player.uuidObject)
        Bukkit.getServer().servicesManager.getRegistration(Economy::class.java).provider.apply {
            val money = getBalance(olpl)
            withdrawPlayer(olpl, money)
        }
    }

    private fun flagDeleter(player: IPlayer<*>) {
        FlagManager.clearFlags(player.bukkit)
    }

    private fun sapiAccountDeleter(api: NpcAPI, player: IPlayer<*>) {
        for (i in 1..10) {
            api.executeCommand(player.world, "class forcereset ${player.name} $i")
        }
    }

    private fun accountDeleter(api: NpcAPI, player: IPlayer<*>) {
        api.executeCommand(player.world, "authme unregister ${player.name}")
    }

    @Suppress("UNCHECKED_CAST")
    fun <D> ICustomGui.get(id: Int): D = this.getComponent(id)!! as D
}

fun result() = Migrate::class.java