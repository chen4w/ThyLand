
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

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.sc.contract._

/**
 * 资产管理合约
 */

class ContractAssetsTPL extends IContract{
case class Transfer(from:String, to:String, amount:Int)

  implicit val formats = DefaultFormats
  
    def init(ctx: ContractContext){      
      println(s"tid: $ctx.t.txid")
    }
    
    def set(ctx: ContractContext, data:Map[String,Int]):Object={
      println(s"set data:$data")
      for((k,v)<-data){
        ctx.api.setVal(k, v)
      }
      null
    }
    
    def transfer(ctx: ContractContext, data:Transfer):Object={
      val sfrom =  ctx.api.getVal(data.from)
      var dfrom =sfrom.toString.toInt
      if(dfrom < data.amount)
        throw new Exception("余额不足")
      var dto = ctx.api.getVal(data.to).toString.toInt
      ctx.api.setVal(data.from,dfrom - data.amount)
      ctx.api.setVal(data.to,dto + data.amount)
      "transfer ok"
    }
    /**
     * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
     */
    def onAction(ctx: ContractContext,action:String, sdata:String ):Object={
      //println(s"onAction---")
      //return "transfer ok"
      val json = parse(sdata)
      
      action match {
        case "transfer" => 
          println(s"transfer oook")
          transfer(ctx,json.extract[Transfer])
        case "set" => 
          println(s"set") 
          set(ctx, json.extract[Map[String,Int]])
      }
    }
    
}