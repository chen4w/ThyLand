/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.network.base

import akka.actor.{Actor, Address, ActorRef}
import rep.app.system.ClusterSystem
import rep.network.cluster.ClusterActor
import rep.network.tools.PeerExtension
import rep.crypto.Sha256
import scala.collection.mutable
import org.slf4j.LoggerFactory
import rep.log.trace.{ModuleType,RepLogger,RepTimeTracer}
import rep.utils.GlobalUtils.{ActorType}


/**
  * 模块基础类伴生对象
  *
  * @author shidianyue
  * @version 1.0
  * 
  * @update 2018-05 jiangbuyun
  **/
object ModuleBase {
}

/**
  * 系统模块基础类
  *
  * @author shidianyue
  * @version 1.0
  * @param name 模块名称
  **/

abstract class  ModuleBase(name: String) extends Actor  with ClusterActor with BaseActor{
  val logPrefix = name
  val pe = PeerExtension(context.system)
  
  val atype = ModuleNameToIntActorType
  atype match{
    case 0 => 
    case _ => pe.register(atype, self)
  }
  
  private def ModuleNameToIntActorType:Int={
    name match{
      case "memberlistener" => 1
      case "modulemanager" => 2
      case "webapi" => 3
      case "peerhelper" => 4
      case "blocker" => 5
      case "preloaderoftransaction" => 6
      case "endorser" => 7
      case "voter" => 8
      case "synchrequester" => 9
      case "transactionpool" => 10
      case "storager" => 11
      case "consensusmanager" => 12
      case "statiscollecter" => 13
      case "endorsementcollectioner" => 14
      case "endorsementrequester" => 15
      case "confirmerofblock" => 16
      case "gensisblock"  => 17
      case "synchresponser" => 18
      case _ => 0
    }
  }
  
    
  /**
    * 日志封装
    *
    * @param lOG_TYPE
    * @param msg
    */
  def logMsg( msg: String): Unit = {
    RepLogger.logInfo(pe.getSysTag, ModuleType.modulebase,  msg + " ~ " + selfAddr)
  }

  
  def logMsg(logtype: rep.log.trace.LogType.LogType, msg: String): Unit = {
    RepLogger.logInfo(pe.getSysTag, ModuleType.modulebase,  msg + " ~ " + selfAddr)
  }
  /**
    * 事件时间戳封装
    *
    * @param msg
    * @param step
    * @param actorRef
    */
  def logTime(timetag:String,time:Long,isstart:Boolean): Unit = {
    if(isstart){
      RepTimeTracer.setStartTime(pe.getSysTag, timetag, time)
    }else{
      RepTimeTracer.setEndTime(pe.getSysTag, timetag, time)
    }
  }
}

