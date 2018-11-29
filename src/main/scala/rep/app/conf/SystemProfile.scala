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

package rep.app.conf

import com.typesafe.config.Config
//import collection.JavaConversions._
//import scala.collection.immutable._
import java.util.List
import java.util.ArrayList

/**
  * 系统配置信息缓存对象
  * @author shidianyue
  * @version	0.7
  * @update 2018-05 jiangbuyun
  * */
object SystemProfile {

  /**
    * 交易创建类型
    */
  case object Trans_Create_Type_Enum {
    val MANUAL = 0 //API创建
    val AUTO = 1 //自动创建
  }

  private[this] var _LIMIT_BLOCK_TRANS_NUM: Int = 0//块内最多交易数
  private[this] var _MIN_BLOCK_TRANS_NUM: Int = 0//块内最少交易数
  private[this] var _VOTE_NOTE_MIN: Int = 0//投票最少参与人数
  private[this] var _TRAN_CREATE_DUR: Int = 0//交易创建时间间隔-针对自动创建
  private[this] var _TRANS_CREATE_TYPE: Int = 0//交易创建类型
  private[this] var _RETRY_TIME: Int = 0//投票重试次数限制
  private[this] var _MAX_CATCH_TRANS_NUM: Int = 0//交易最多缓存数量
  private[this] var _DISKSPACE_ALARM_NUM:Long=0//磁盘剩余空间预警 单位=M
  private[this] var _SERVERPORT:Int=8081//http服务的端口，默认为8081
  private[this] var _CHECKCERTVALIDATE:Int=0//是否检查证书的有效性，0不检查，1检查
  private[this] var _CONTRACTOPERATIONMODE = 0//设置合约的运行方式，0=debug方式，1=deploy，默认为debug方式，如果发布部署，必须使用deploy方式。
  private[this] var _SIGNATUREALGORITHM = "SHA1WITHECDSA"// 设置签名算法，默认用开源
  private[this] var _DIGESTALOGORITHM = "SHA-256"// 设置散列算法，默认用开源
  private[this] var _KEYSTORE = "jks" //设置keystore存储格式，默认用开源
  private[this] var _VOTENODELIST : List[String] = new ArrayList[String]
  

  private def SERVERPORT :Int = _SERVERPORT
  private def CHECKCERTVALIDATE:Int = _CHECKCERTVALIDATE
  private def DISKSPACE_ALARM_NUM :Long = _DISKSPACE_ALARM_NUM
  private def CONTRACTOPERATIONMODE:Int=_CONTRACTOPERATIONMODE
  private def SIGNATUREALGORITHM:String = _SIGNATUREALGORITHM
  private def DIGESTALOGORITHM:String = _DIGESTALOGORITHM
  private def KEYSTORE:String = _KEYSTORE
  
  private def VOTENODELIST : List[String] = _VOTENODELIST
  
  private def VOTENODELIST_=(value: List[String]): Unit = {
      _VOTENODELIST = value
  }
  
  
  private def SERVERPORT_=(value: Int): Unit = {
    _SERVERPORT = value
  }

  private def CHECKCERTVALIDATE_=(value: Int): Unit = {
    _CHECKCERTVALIDATE = value
  }
  
  private def CONTRACTOPERATIONMODE_=(value: Int): Unit = {
    _CONTRACTOPERATIONMODE = value
  }

  private def SIGNATUREALGORITHM_=(value: String): Unit = {
    _SIGNATUREALGORITHM = value
  }

  private def DIGESTALOGORITHM_=(value:String): Unit = {
    _DIGESTALOGORITHM = value
  }

  private def KEYSTORE_=(value:String): Unit = {
    _KEYSTORE = value
  }
  
  private def DISKSPACE_ALARM_NUM_=(value: Long): Unit = {
    _DISKSPACE_ALARM_NUM = value
  }
  
  private def MAX_CATCH_TRANS_NUM: Int = _MAX_CATCH_TRANS_NUM

  private def MAX_CATCH_TRANS_NUM_=(value: Int): Unit = {
    _MAX_CATCH_TRANS_NUM = value
  }

  private def RETRY_TIME: Int = _RETRY_TIME
  

  private def RETRY_TIME_=(value: Int): Unit = {
    _RETRY_TIME = value
  }


  private def TRANS_CREATE_TYPE: Int = _TRANS_CREATE_TYPE

  private def TRANS_CREATE_TYPE_=(value: Int): Unit = {
    _TRANS_CREATE_TYPE = value
  }

  private def TRAN_CREATE_DUR: Int = _TRAN_CREATE_DUR

  private def TRAN_CREATE_DUR_=(value: Int): Unit = {
    _TRAN_CREATE_DUR = value
  }

  private def VOTE_NOTE_MIN: Int = _VOTE_NOTE_MIN

  private def VOTE_NOTE_MIN_=(value: Int): Unit = {
    _VOTE_NOTE_MIN = value
  }

  private def MIN_BLOCK_TRANS_NUM: Int = _MIN_BLOCK_TRANS_NUM

  private def MIN_BLOCK_TRANS_NUM_=(value: Int): Unit = {
    _MIN_BLOCK_TRANS_NUM = value
  }

  private def LIMIT_BLOCK_TRANS_NUM: Int = _LIMIT_BLOCK_TRANS_NUM

  private def LIMIT_BLOCK_TRANS_NUM_=(value: Int): Unit = {
    _LIMIT_BLOCK_TRANS_NUM = value
  }

  
  /**
    * 初始化配饰信息
    * @param config
    */
  def initConfigSystem(config:Config): Unit ={
    LIMIT_BLOCK_TRANS_NUM_=(config.getInt("system.block.trans_num_limit"))
    MIN_BLOCK_TRANS_NUM_=(config.getInt("system.block.trans_num_min"))
    RETRY_TIME_=(config.getInt("system.block.retry_time"))
    VOTE_NOTE_MIN_=(config.getInt("system.vote.vote_note_min"))
    VOTENODELIST_=(config.getStringList("system.vote.vote_node_list"))
    TRAN_CREATE_DUR_=(config.getInt("system.transaction.tran_create_dur"))
    MAX_CATCH_TRANS_NUM_=(config.getInt("system.transaction.max_cache_num"))
    TRANS_CREATE_TYPE_=(config.getInt("system.trans_create_type"))
    DISKSPACE_ALARM_NUM_=(config.getInt("system.diskspaceManager.diskspacealarm"))
    SERVERPORT_=(config.getInt("system.httpServicePort"))
    CHECKCERTVALIDATE_=(config.getInt("system.checkCertValidate"))
    CONTRACTOPERATIONMODE_=(config.getInt("system.contractOperationMode"))
    SIGNATUREALGORITHM_=(config.getString("system.signAlgorithm"))
    DIGESTALOGORITHM_=((config.getString("system.digestAlgorithm")))
    KEYSTORE_=((config.getString("system.keyStore")))
  }
  
  def getLimitBlockTransNum = LIMIT_BLOCK_TRANS_NUM

  def getMinBlockTransNum = MIN_BLOCK_TRANS_NUM

  def getVoteNoteMin = VOTE_NOTE_MIN

  def getTranCreateDur = TRAN_CREATE_DUR

  def getMaxCacheTransNum = MAX_CATCH_TRANS_NUM

  def getTransCreateType = TRANS_CREATE_TYPE

  def getRetryTime = RETRY_TIME
  
  def getDiskSpaceAlarm = DISKSPACE_ALARM_NUM
  
  def getHttpServicePort = SERVERPORT
  
  def getCheckCertValidate = CHECKCERTVALIDATE
  
  def getContractOperationMode = CONTRACTOPERATIONMODE

  def getSignatureAlgorithm = SIGNATUREALGORITHM

  def getDigestAlogorithm = DIGESTALOGORITHM

  def getKeyStore = KEYSTORE
  
  def getVoteNodeList = VOTENODELIST

}
