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
//zhj
package rep.network.confirmblock.pbft

import akka.actor.{ActorRef, Props}
import akka.util.Timeout
import rep.app.conf.SystemProfile
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.confirmblock.IConfirmOfBlock
import rep.network.consensus.common.MsgOfConsensus
import rep.network.consensus.common.MsgOfConsensus.{BatchStore, BlockRestore}
import rep.network.consensus.pbft.MsgOfPBFT
import rep.network.consensus.util.BlockVerify
import rep.network.module.ModuleActorType.ActorType
import rep.network.persistence.IStorager.SourceOfBlock
import rep.utils.GlobalUtils.EventType

import scala.concurrent._

object ConfirmOfBlockOfPBFT {
  def props(name: String): Props = Props(classOf[ConfirmOfBlockOfPBFT], name)
}

class ConfirmOfBlockOfPBFT(moduleName: String) extends IConfirmOfBlock(moduleName) {
  import context.dispatcher

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("confirm Block module start"))
    SubscribeTopic(mediator, self, selfAddr, Topic.Block, false)
  }
  import rep.protos.peer._

  import scala.concurrent.duration._

  case class DataSig(data:Array[Byte], sig : Signature)

  private def asyncVerifyEndorses(block: Block, replies : Seq[MPbftReply]): Boolean = {
    val b = block.clearEndorsements.toByteArray

    val ds = scala.collection.mutable.Buffer[DataSig]()
    replies.foreach( r => {
        ds += DataSig(r.clearSignature.toByteArray, r.signature.get)
      for (c <- r.commits) {
          ds += DataSig(c.clearSignature.toByteArray, c.signature.get)
          c.prepares.foreach(p=>{
            ds += DataSig(b, p.signature.get)
          })
      }
    })

    /*val listOfFuture: Seq[Future[Boolean]] = block.endorsements.map(x => {
      asyncVerifyEndorse(x, b)
    }) */

    val listOfFuture: Seq[Future[Boolean]] = ds.map(x => {
      asyncVerifyEndorse(x.sig, x.data)
    })

    val futureOfList: Future[List[Boolean]] = Future.sequence(listOfFuture.toList).recover({
      case e: Exception =>
        null
    })

    val result1 = Await.result(futureOfList, timeout.duration).asInstanceOf[List[Boolean]]

    var result = true
    if (result1 == null) {
      result = false
    } else {
      result1.foreach(f => {
        if (!f) {
          result = false
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"comfirmOfBlock verify endorse is error, break,block height=${block.height},local height=${pe.getCurrentHeight}"))
        }
      })
    }

    result
  }

  protected def handler(block: Block, actRefOfBlock: ActorRef): Unit ={
    RepLogger.error(RepLogger.Consensus_Logger,pe.getSysTag + ", Internal error, ConfirmOfBlockOfPBFT.handler")
  }

  private def handler(block: Block, actRefOfBlock: ActorRef, replies : Seq[MPbftReply]) = {
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement start,height=${block.height}"))
    var b = true
    if (SystemProfile.getIsVerifyOfEndorsement)
        b = asyncVerifyEndorses(block,replies)
    if (b) {
        pe.getBlockCacheMgr.addToCache(BlockRestore(block, SourceOfBlock.CONFIRMED_BLOCK, actRefOfBlock))
        pe.getActorRef(ActorType.storager) ! BatchStore
        sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.BLOCK_NEW)
    }
  }

  override protected def checkedOfConfirmBlock(block: Block, actRefOfBlock: ActorRef): Unit ={
    RepLogger.error(RepLogger.Consensus_Logger,pe.getSysTag + ", Internal error, ConfirmOfBlockOfPBFT.checkedOfConfirmBlock")
  }

  private def checkedOfConfirmBlock(block: Block, actRefOfBlock: ActorRef, replies : Seq[MPbftReply]) = {
    if (pe.getCurrentBlockHash == "" && block.previousBlockHash.isEmpty()) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify blockhash,height=${block.height}"))
      handler(block, actRefOfBlock, replies)
    } else {
      //与上一个块一致
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify blockhash,height=${block.height}"))

      /* if (SystemProfile.getNumberOfEndorsement == 1) {
        if (block.height > pe.getCurrentHeight + 1) {
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify height,height=${block.height}，localheight=${pe.getCurrentHeight }"))
          pe.getActorRef(ActorType.synchrequester) ! SyncRequestOfStorager(sender,block.height)
        } else {
          handler(block, actRefOfBlock)
          pe.setConfirmHeight(block.height)
        }
      } else { */
        if ( replies.size >= (SystemProfile.getPbftF + 1))
            handler(block, actRefOfBlock, replies)
      //}
    }
  }

  override def receive = {
    //Endorsement block
    case MsgOfConsensus.ConfirmedBlock(block, actRefOfBlock) =>
      //RepLogger.print(RepLogger.zLogger,pe.getSysTag + ", ConfirmOfBlockOfPBFT recv ConfirmedBlock(2 params): " + ", " + block.hashOfBlock)
      RepTimeTracer.setStartTime(pe.getSysTag, "blockconfirm", System.currentTimeMillis(), block.height, block.transactions.size)
      checkedOfConfirmBlock(block, actRefOfBlock, Seq.empty)
      RepTimeTracer.setEndTime(pe.getSysTag, "blockconfirm", System.currentTimeMillis(), block.height, block.transactions.size)
    case MsgOfPBFT.ConfirmedBlock(block, actRefOfBlock, replies) =>
      //RepLogger.print(RepLogger.zLogger,pe.getSysTag + ", ConfirmOfBlockOfPBFT recv ConfirmedBlock: " + ", " + block.hashOfBlock)
      RepTimeTracer.setStartTime(pe.getSysTag, "blockconfirm", System.currentTimeMillis(), block.height, block.transactions.size)
      checkedOfConfirmBlock(block, actRefOfBlock, replies)
      RepTimeTracer.setEndTime(pe.getSysTag, "blockconfirm", System.currentTimeMillis(), block.height, block.transactions.size)
    case _ => //ignore
  }

}