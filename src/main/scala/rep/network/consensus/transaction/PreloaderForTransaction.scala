package rep.network.consensus.transaction

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ Actor, ActorRef, Props }
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.consensus.block.Blocker.{ PreTransBlock, PreTransBlockResult}
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.sc.TransProcessor.DoTransaction
import rep.sc.{ Sandbox, TransProcessor }
import rep.sc.Sandbox.DoTransactionResult
import rep.storage.{ ImpDataPreloadMgr }
import rep.utils.GlobalUtils.ActorType
import rep.utils._
import scala.collection.mutable
import rep.log.trace.LogType
import akka.pattern.AskTimeoutException

object PreloaderForTransaction {
  def props(name: String, transProcessor: ActorRef): Props = Props(classOf[PreloaderForTransaction], name, transProcessor)
}

class PreloaderForTransaction(moduleName: String, transProcessor: ActorRef) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.collection.breakOut
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload seconds)

  override def preStart(): Unit = {
    logMsg(LogType.INFO, "PreloaderForTransaction Start")
  }

  private def ExecuteTransaction(t: Transaction, db_identifier: String): (Int, DoTransactionResult) = {
    try {
      val future = transProcessor ? new DoTransaction(t, self, db_identifier)
      val result = Await.result(future, timeout.duration).asInstanceOf[DoTransactionResult]
      (0, result)
    } catch {
      case e: AskTimeoutException => (1, null)
    }
  }

  private def AssembleTransResult(block:Block,preLoadTrans:mutable.HashMap[String,Transaction],transResult:Seq[TransactionResult], db_indentifier: String):Option[Block]={
    try{
      var newTranList = mutable.Seq.empty[ Transaction ]
      for (tran <- block.transactions) {
        if (preLoadTrans.getOrElse(tran.id, None) != None)
          newTranList = newTranList :+ preLoadTrans(tran.id)
      }
      if(newTranList.size > 0){
        val tmpblk = block.withTransactions(newTranList)
        var rblock = tmpblk.withTransactionResults(transResult)
        Some(rblock)
      }else{
        None
      }
    }catch{
      case e:RuntimeException=>
        logMsg(LogType.ERROR, s" AssembleTransResult error, error: ${e.getMessage}")
        None
    }finally{
      ImpDataPreloadMgr.Free(pe.getSysTag,db_indentifier)
    }
  }

  def Handler(t: Transaction, preLoadTrans: mutable.HashMap[String, Transaction], db_indentifier: String): Option[TransactionResult] = {
    try {
      val result = ExecuteTransaction(t, db_indentifier)
      result._1 match {
        case 0 =>
          //finish
          val r = result._2
          r.err match {
            case None =>
              //success
              var ts = TransactionResult(t.id, r.ol.toSeq, Option(r.r))
              Some(ts)
            case _ =>
              //error
              preLoadTrans.remove(t.id)
              logMsg(LogType.ERROR, s"${t.id} preload error, error: ${r.err}")
              None
          }
        case _ =>
          // timeout failed
          preLoadTrans.remove(t.id)
          logMsg(LogType.ERROR, s"${t.id} preload error, error: timeout")
          None
      }
    } catch {
      case e: RuntimeException =>
        preLoadTrans.remove(t.id)
        logMsg(LogType.ERROR, s"${t.id} preload error, error: ${e.getMessage}")
        None
    }
  }

  override def receive = {
    case PreTransBlock(block,prefixOfDbTag) =>
      logTime("block preload inner time", System.currentTimeMillis(), true)
      if ((block.previousBlockHash.toStringUtf8().equals(pe.getCurrentBlockHash) || block.previousBlockHash == ByteString.EMPTY) &&
        block.height == (pe.getCurrentHeight + 1)) {
        var preLoadTrans = mutable.HashMap.empty[String, Transaction]
        preLoadTrans = block.transactions.map(trans => (trans.id, trans))(breakOut): mutable.HashMap[String, Transaction]
        var transResult = Seq.empty[rep.protos.peer.TransactionResult]
        val dbtag = prefixOfDbTag+"_"+block.transactions.head.id
        //确保提交的时候顺序执行
        block.transactions.map(t => {
          var ts = Handler(t, preLoadTrans, dbtag)
          if(ts != None){
            transResult = (transResult :+ ts.get)
          }
        })
        
        var newblock = AssembleTransResult(block,preLoadTrans,transResult,dbtag)
        
        if(newblock == None){
          //所有交易执行失败
          logMsg(LogType.ERROR, s" All Transaction failed, error: ${block.height}")
          sender ! PreTransBlockResult(null,false)
        }else{
          //全部或者部分交易成功
          sender ! PreTransBlockResult(newblock.get,true)
        }
        logTime("block preload inner time", System.currentTimeMillis(), false)
      }

    case _ => //ignore
  }
}