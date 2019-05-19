/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.network.consensus.block

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ ActorRef, Address, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.crypto.Sha256
import rep.network.consensus.vote.Voter.VoteOfBlocker
import rep.network.base.ModuleBase
import rep.network.consensus.block.Blocker.{ ConfirmedBlock, PreTransBlock, PreTransBlockResult }
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import scala.collection.mutable
import com.sun.beans.decoder.FalseElementHandler
import scala.util.control.Breaks
import rep.utils.IdTool
import scala.util.control.Breaks._
import rep.network.consensus.util.{ BlockHelp, BlockVerify }
import rep.network.util.NodeHelp
import rep.network.Topic
import rep.network.consensus.endorse.EndorseMsg
import rep.log.RepLogger
import rep.log.RepTimeTracer

object Blocker {
  def props(name: String): Props = Props(classOf[Blocker], name)

  case class PreTransBlock(block: Block, prefixOfDbTag: String)
  //块预执行结果
  case class PreTransBlockResult(blc: Block, result: Boolean)

  //正式块
  case class ConfirmedBlock(blc: Block, actRef: ActorRef)

  case object CreateBlock

  case object EndorseOfBlockTimeOut

}

/**
 * 出块模块
 *
 * @author shidianyue
 * @version 1.0
 * @since 1.0
 * @param moduleName 模块名称
 */
class Blocker(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  import akka.actor.ActorSelection
  import scala.collection.mutable.ArrayBuffer
  import rep.protos.peer.{ Transaction }

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload seconds)

  var preblock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("Block module start"))
    //SubscribeTopic(mediator, self, selfAddr, Topic.Block, true)
    //scheduler.scheduleOnce(TimePolicy.getStableTimeDur millis, context.parent, BlockModuleInitFinished)
  }

  private def CollectedTransOfBlock(start:Int,num: Int, limitsize: Int): Seq[Transaction] = {
    val result = ArrayBuffer.empty[Transaction]
    try {
      val tmplist = pe.getTransPoolMgr.getTransListClone(num,start)
      if (tmplist.size > 0) {
        val currenttime = System.currentTimeMillis() / 1000
        val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
        var transsize = 0
        breakable(
          tmplist.foreach(f => {
            //判断交易是否超时，把超时的交易删除
            if ((currenttime - f.createTime) > TimePolicy.getTranscationWaiting) {
              pe.getTransPoolMgr.removeTranscation(f.t)
            } else {
              //判断交易是否已经被打包入块，如果已经打包入块需要删除
              if (sr.getBlockByTxId(f.t.id) != null) {
                pe.getTransPoolMgr.removeTranscation(f.t)
              } else {
                transsize += f.t.toByteArray.size
                if (transsize * 3 > limitsize) {
                  //区块的长度限制
                  break
                } else {
                  f.t +=: result
                }
              }
            }
          }))
      }
    } finally {
    }
    result.reverse
  }

  private def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      //val future = pe.getActorRef(ActorType.preloaderoftransaction) ? Blocker.PreTransBlock(block, "preload")
      val future = pe.getActorRef(ActorType.dispatchofpreload) ? Blocker.PreTransBlock(block, "preload")
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        result.blc
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException => null
    }
  }
  

  private def CreateBlock(start:Int=0): Block = {
    RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(),pe.getCurrentHeight + 1,0)
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(),pe.getCurrentHeight + 1,0)
    RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(),pe.getCurrentHeight + 1,0)
    val trans = CollectedTransOfBlock(start,SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength)
    //todo 交易排序
    if (trans.size >= SystemProfile.getMinBlockTransNum) {
      RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(),pe.getCurrentHeight + 1,trans.size)
      //此处建立新块必须采用抽签模块的抽签结果来进行出块，否则出现刚抽完签，马上有新块的存储完成，就会出现错误
      var blc = BlockHelp.WaitingForExecutionOfBlock(pe.getBlocker.voteBlockHash, pe.getBlocker.VoteHeight + 1, trans)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
       RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(),blc.height,blc.transactions.size)
      blc = ExecuteTransactionOfBlock(blc)
      if (blc != null) {
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(),blc.height,blc.transactions.size)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
        blc = BlockHelp.AddBlockHash(blc)
        BlockHelp.AddSignToBlock(blc, pe.getSysTag)
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,preload error" + "~" + selfAddr))
        CreateBlock(start+trans.size)
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,trans count error" + "~" + selfAddr))
      null
    }
  }

  private def CreateBlockHandler = {
    //if (preblock == null) {
    val blc = CreateBlock(0)
    if (blc != null) {
      RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(),blc.height,blc.transactions.size)
      this.preblock = blc
      schedulerLink = clearSched()
      //在发出背书时，告诉对方我是当前出块人，取出系统的名称
      //pe.getActorRef(ActorType.endorsementcollectioner) ! EndorseMsg.CollectEndorsement(this.preblock, pe.getBlocker.blocker)
      RepTimeTracer.setStartTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(),blc.height,blc.transactions.size)
      pe.getActorRef(ActorType.endorsementcollectioner) ! EndorseMsg.CollectEndorsement(this.preblock, pe.getSysTag)
      //schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, Blocker.EndorseOfBlockTimeOut)
    }
    //}
  }

  override def receive = {
    //创建块请求（给出块人）
    case Blocker.CreateBlock =>
      if (!pe.isSynching) {
        if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag) && pe.getBlocker.voteBlockHash == pe.getCurrentBlockHash) {
          sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)

          //是出块节点
          if (preblock == null ||  (preblock.previousBlockHash.toStringUtf8() != pe.getBlocker.voteBlockHash)) {
            CreateBlockHandler
          }
        } else {
          //出块标识错误,暂时不用做任何处理
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,do not blocker or blocker hash not equal current hash,height=${this.preblock.height}" + "~" + selfAddr))
        }
      } else {
        //节点状态不对
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node status error,status is synching,height=${this.preblock.height}" + "~" + selfAddr))
      }

    case _ => //ignore
  }

}