package rep.network.sync.request.raft

import akka.actor.Props
import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.network.module.{IModuleManager, ModuleActorType}
import rep.network.sync.SyncMsg.{MaxBlockInfo, StartSync, SyncRequestOfStorager}
import rep.network.sync.parser.ISynchAnalyzer
import rep.network.sync.parser.raft.IRAFTSynchAnalyzer
import rep.network.sync.request.ISynchRequester
import rep.network.util.NodeHelp

import scala.concurrent.duration._

/**
 * Created by jiangbuyun on 2020/03/18.
 * 基于RAFT共识协议的同步actor的实现类
 */

object SynchRequesterOfRAFT{
  def props(name: String): Props = Props(classOf[SynchRequesterOfRAFT], name)
}

class SynchRequesterOfRAFT (moduleName: String) extends ISynchRequester(moduleName: String)  {
  import context.dispatcher

  override protected def getAnalyzerInSynch: ISynchAnalyzer = {
    new IRAFTSynchAnalyzer(pe.getSysTag, pe.getSystemCurrentChainStatus, pe.getNodeMgr)
  }

  override def receive: Receive = {
    case StartSync(isNoticeModuleMgr: Boolean) =>
      schedulerLink = clearSched()
      var rb = true
      initSystemChainInfo
      if (pe.getNodeMgr.getStableNodes.size >= SystemProfile.getVoteNoteMin && !pe.isSynching) {
        pe.setSynching(true)
        try {
          val ssize = pe.getNodeMgr.getStableNodes.size
          if(SystemProfile.getVoteNodeList.size() == ssize){
            rb = Handler(isNoticeModuleMgr)
          }
        } catch {
          case e: Exception =>
            rb = false
            RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"request synch excep,msg=${e.getMessage}"))
        }
        pe.setSynching(false)
        if (rb) {
          if (isNoticeModuleMgr)
            pe.getActorRef(ModuleActorType.ActorType.modulemanager) ! IModuleManager.startup_Consensus
        } else {
          schedulerLink = scheduler.scheduleOnce(1.second, self, StartSync(isNoticeModuleMgr))
        }

      } else {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"too few node,min=${SystemProfile.getVoteNoteMin} or synching  from actorAddr" + "～" + NodeHelp.getNodePath(sender())))
      }

    case SyncRequestOfStorager(responser, maxHeight) =>
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry blockdata synch,maxheight=${maxHeight},responser=${responser}"))
      if (!pe.isSynching) {
        pe.setSynching(true)
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"start blockdata synch,currentheight=${pe.getCurrentHeight},maxheight=${maxHeight}"))
        try {
          getBlockDatas(pe.getCurrentHeight, maxHeight, responser)
        } catch {
          case e: Exception =>
            pe.setSynching(false)
        }
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"stop blockdata synch,maxheight=${maxHeight}"))
        pe.setSynching(false)
      }
  }

  override protected def setStartVoteInfo(maxblockinfo:MaxBlockInfo): Unit = {
    pe.setStartVoteInfo(maxblockinfo)
  }
}
