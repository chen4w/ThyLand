package rep.network.sync


import rep.network.util.NodeHelp
import scala.collection._
import scala.util.control.Breaks._
import rep.protos.peer._
import com.google.protobuf.ByteString

object SyncHelp {
  
  def GetGreatMajorityHeight(ResultList:immutable.TreeMap[String, SynchronizeRequester.ResponseInfo],localHeight:Long,nodecount:Int,systemname:String): SynchronizeRequester.GreatMajority = {
    var tmpmap = new immutable.TreeMap[Long, SynchronizeRequester.GreatMajority]()
    if(systemname == "122000002n00123567.node3" && ResultList.size == 4){
      println(ResultList.size)
    }
    ResultList.foreach(f => {
      val addr = f._1
      val hash = f._2.response.currentBlockHash.toStringUtf8()
      val height = f._2.response.height
      var tmp: SynchronizeRequester.GreatMajority = null
      if (tmpmap.contains(height)) {
        tmp = tmpmap(height)
        if (tmp.lastHash.equals(hash)) {
          tmp = SynchronizeRequester.GreatMajority(tmp.addr, height, hash, tmp.count + 1)
        }
      } else {
        tmp = SynchronizeRequester.GreatMajority(addr, height, hash, 1)
      }
      tmpmap += height -> tmp
    })
    
    var majority = getHeight(tmpmap,localHeight,nodecount)
    
    if(majority != null){
      if(majority.height <= 0){
        if(ResultList.size  >= (nodecount - 1)){
          majority = findGenesisNode(tmpmap)
        }else{
          //节点数不够，继续等待
          majority = null
        }
      }
    }
    
    majority
  }
  
  private def findGenesisNode(list: immutable.TreeMap[Long, SynchronizeRequester.GreatMajority]): SynchronizeRequester.GreatMajority ={
    var max: SynchronizeRequester.GreatMajority = null
    val keylist = list.keys.toArray
    if(keylist.size == 2){
      max = list(keylist(1))
    }
    max
  }

  private def getHeight(list: immutable.TreeMap[Long, SynchronizeRequester.GreatMajority],localHeight:Long,nodecount:Int): SynchronizeRequester.GreatMajority = {
    var max: SynchronizeRequester.GreatMajority = null
    var tmpHeight : Long = -1
    var tmpcount = 0
    val keylist = list.keys.toArray
    var i = keylist.size -1 
    breakable(
        while(i >= 0){
          val tmp = list(keylist(i))
          if(tmp.height >= localHeight){
            if(NodeHelp.ConsensusConditionChecked(tmp.count, nodecount-1)){
              max = tmp
              break
            }else{
              tmpHeight = tmp.height
              tmpcount += tmp.count
            }
          }else{
            break
          }
          i -= 1
        }
    )
    if(max == null){
      if(tmpHeight > -1 && NodeHelp.ConsensusConditionChecked(tmpcount, nodecount-1)){
        val tmp = list(tmpHeight)
        max = SynchronizeRequester.GreatMajority(tmp.addr,tmp.height,tmp.lastHash,tmpcount)
      }
    }
    
    max
  }
  
  
}