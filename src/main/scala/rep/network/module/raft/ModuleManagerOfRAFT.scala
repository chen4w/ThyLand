package rep.network.module.raft

import akka.actor.Props
import rep.log.RepLogger
import rep.network.cache.raft.TransactionPoolOfRAFT
import rep.network.confirmblock.raft.ConfirmBlockOfRAFT
import rep.network.module.{IModuleManager, ModuleActorType}
import rep.network.module.cfrd.CFRDActorType
import rep.network.sync.response.SynchronizeResponser
import rep.network.consensus.cfrd.MsgOfCFRD.VoteOfBlocker
import rep.network.consensus.raft.block.BlockerOfRAFT
import rep.network.consensus.raft.vote.VoterOfRAFT
import rep.network.persistence.raft.StoragerOfRAFT
import rep.network.sync.request.raft.SynchRequesterOfRAFT

/**
 * Created by jiangbuyun on 2020/03/19.
 * 基于RAFT共识的模块管理actor
 */

object ModuleManagerOfRAFT{
  def props(name: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean): Props = Props(classOf[ModuleManagerOfRAFT], name, sysTag, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean)

}

class ModuleManagerOfRAFT(moduleName: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean) extends IModuleManager(moduleName,sysTag, enableStatistic, enableWebSocket, isStartup){
  override def preStart(): Unit = {
    RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix( "ModuleManagerOfRAFT Start"))
  }

  //启动共识模块，启动CFRD共识
  override def startupConsensus: Unit = {
    pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
  }

  override def loadConsensusModule = {
    pe.register(ModuleActorType.ActorType.transactionpool, context.actorOf(TransactionPoolOfRAFT.props("transactionpool"), "transactionpool"))
    pe.register(ModuleActorType.ActorType.storager,context.actorOf(StoragerOfRAFT.props("storager"), "storager"))
    pe.register(CFRDActorType.ActorType.blocker,context.actorOf(BlockerOfRAFT.props("blocker"), "blocker"))
    pe.register(CFRDActorType.ActorType.confirmerofblock,context.actorOf(ConfirmBlockOfRAFT.props("confirmerofblock"), "confirmerofblock"))
    pe.register(CFRDActorType.ActorType.voter,context.actorOf(VoterOfRAFT.props("voter"), "voter"))

    pe.register(CFRDActorType.ActorType.synchrequester,context.actorOf(SynchRequesterOfRAFT.props("synchrequester"), "synchrequester"))
    pe.register(CFRDActorType.ActorType.synchresponser,context.actorOf(SynchronizeResponser.props("synchresponser"), "synchresponser"))
  }
}