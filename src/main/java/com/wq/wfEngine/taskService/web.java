package com.wq.wfEngine.taskService;


import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Future;



import com.wq.wfEngine.activiti.ActivitiUtils;
import com.wq.wfEngine.cache.cachedData;
import com.wq.wfEngine.cache.cachedServiceTaskResult;
import com.wq.wfEngine.cache.monitorIps;
import com.wq.wfEngine.tool.Connect;
import com.wq.wfEngine.tool.inputSelectParser;
import com.wq.wfEngine.tool.jsonTransfer;
import com.wq.wfEngine.tool.simpleJson.simpleJsonParser;

import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.BpmnError;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.db.cache.oidEvents;
import org.activiti.engine.impl.db.workflowClass.serviceTaskRes;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;

public class web implements JavaDelegate {
    //使用监控链调服务的服务名
    private Expression serviceName;
    //调用服务使用的方法
    private Expression httpMethod;
    //调用服务的路径route
    private Expression route;
    //用于指定输入
    private Expression input;
    //s-group
    private Expression serviceGroup;
    //最后一个serviceTask，用于指定businessData输出
    private Expression output;
    //用于将服务调用数据转换为流程数据用于流程流转
    private Expression processVariable;
    //对调用服务的输入值做修饰，额外的操作
    private Expression modify;
    //用于跨组织
    private Expression nextProcess;
    private Expression nextMessage;


    public void execute(DelegateExecution execution) {
        
        try {
            //拿变量
            CommandContext commandContext= Context.getCommandContext();
            String oid=commandContext.getOid();
            String activityId=execution.getCurrentActivityId();
            String serviceTaskName=execution.getCurrentFlowElement().getName();
            //弃用
            // /*
            //  * 使用对称加密之后的hashcode选出执行节点
            //  */
            // if (commandContext.needJudgeExecuteForST()) {
            //     //这里的判断是为了避免重复判断执行节点
            //     // String data = commandContext.getOid();
            //     // String key = execution.getCurrentFlowElement().getName();
            //     //这里向上下文中写入
            //     // int selectNode=hMacMD5.encode(data, key)%NodeNumCache.getAllNodeNum();
            //     // commandContext.setIsExecuteServiceTask(selectNode==NodeNumCache.getThisNodeNum());
            //     String taskName=execution.getCurrentFlowElement().getName();
            //     int hashCode=Math.abs((oid+taskName).hashCode());
            //     //这里向上下文中写入初始的monitor节点的number和是否执行服务调用
            //     commandContext.setMonitorAmount(monitorIps.getMonitorAmount());
            //     commandContext.setMonitorNumber(monitorIps.getInitialMonitorNumber(hashCode));
            //     int selectNode=hashCode%NodeNumCache.getAllNodeNum();
            //     commandContext.setIsExecuteServiceTask(selectNode==NodeNumCache.getThisNodeNum());
            // }
            //serviceName存在，且为当前节点执行
            if (serviceName!=null) {
                Map<String,serviceTaskRes> serviceTaskResMap= commandContext.getServiceTaskRes();
                if (serviceTaskResMap==null) {
                    if (!cachedServiceTaskResult.isCreateMap(oid)) {
                        cachedServiceTaskResult.addServiceTaskRes(new HashMap<String,serviceTaskRes>(), oid);    
                    }
                    String businessData=commandContext.getBusinessData();
                    String lastResponse=commandContext.getLastResponse();


                    //轮询获得monitorIp，底层是return monitorNumber++
                    if (commandContext.getMonitorAmount()==0) {
                        int hashCode=Math.abs((oid+serviceTaskName).hashCode());
                        commandContext.setMonitorAmount(monitorIps.getMonitorAmount());
                        commandContext.setMonitorNumber(monitorIps.getInitialMonitorNumber(hashCode));
                    }
                    String monitorIp=monitorIps.chooseMonitorIp(commandContext.getMonitorNumber());
                    String monitorUrl="http://"+monitorIp+":8999/grafana/run?loadBalance=enabled";


                    Map<String,Object> dataPool=commandContext.getCachedOutput();
                    String body="{}";
                    //input的缺省值
                    if (input==null||input.getValue(execution).toString().equals("default")) {
                        if (lastResponse==null) {
                            body=businessData;
                        } else {
                            body=lastResponse;
                        }
                    } else {
                        body=inputSelectParser.parse(dataPool, input.getValue(execution).toString());
                    }
                    //智慧城市需要，对输入做一些处理
                    if (modify!=null) {
                        Map<String,Object> bodyMap=jsonTransfer.jsonToMap(body);
                        String modifyString=modify.getValue(execution).toString();
                        Map<String,Object> modifyMap=jsonTransfer.jsonToMap(modifyString);
                        StringBuilder valueBuilder=new StringBuilder();
                        for (String modifyKey:modifyMap.keySet()) {
                            if (!bodyMap.containsKey(modifyKey)) {
                                throw new RuntimeException("对key为"+modifyKey+"的值加工失败，因为没有对应的key");
                            }
                            String value=String.valueOf(modifyMap.get(modifyKey));
                            if (!value.contains("${}")) {
                                throw new RuntimeException(value+"格式错误，因为没有找到${}");
                            }
                            int left=value.indexOf("${}");
                            valueBuilder.append(value.substring(0, left)).append(String.valueOf(bodyMap.get(modifyKey)))
                                        .append(value.substring(left+3));
                            bodyMap.put(modifyKey,valueBuilder.toString());
                            valueBuilder.setLength(0);
                        }
                        body=jsonTransfer.mapToJsonString(bodyMap);
                    }
                    /*
                    * 这里做随机选择用post的数据，以oid做密钥，生成一个数
                    * 然后取余，通过对各个节点标记，从而进行选择
                    */

                    /*
                    * 拼装json数据
                    */
                    Map<String,Object> postMap=new HashMap<String,Object>();
                    //s-consumerName用oid@serviceTaskName
                    if (route==null) throw new RuntimeException("serviceName不为空的情况下,route不能为空");
                    if (httpMethod==null) throw new RuntimeException("serviceName不为空的情况下,httpMethod不能为空");
                    postMap.put("s-consumerName",commandContext.getOid()+"@"+execution.getCurrentFlowElement().getName());
                    postMap.put("s-serviceName",serviceName.getValue(execution).toString());
                    postMap.put("headers","{}");
                    postMap.put("s-url",route.getValue(execution).toString());
                    postMap.put("s-method",httpMethod.getValue(execution).toString());
                    postMap.put("body",body);
                    if (serviceGroup!=null) {
                        postMap.put("s-group",serviceGroup.getValue(execution).toString());
                    }
                    /*
                    * 这里做随机选择用post的数据，以oid做密钥，生成一个数
                    * 然后取余，通过对各个节点标记，从而进行选择
                    */
                    //表明要请求服务
                    Future<SimpleHttpResponse> future = Connect.doPost(monitorUrl, jsonTransfer.mapToJsonString(postMap));
                    SimpleHttpResponse response = future.get();
                    /*
                    * 一个线程里如果同时只有一个activiti的命令在运行，则上下文栈里只有唯一一个上下文
                    * 可以通过增加上下文中的属性，对属性进行改写来达到一个数据传递的作用
                    * 这里改写的是业务数据
                    * 将返回的数据，保存为业务数据
                    */
                    if (response.getCode()!=200) {
                        //System.out.println("web service request error,response:"+response.getBodyText());
                        if (cachedData.hasErrorBoundaryEvent(activityId, oid.split("@")[0])) {
                            serviceTaskRes result=new serviceTaskRes();
                            result.setStatus(false);
                            result.setBody(response.getBodyText());
                            cachedServiceTaskResult.getServiceTaskRes(oid).put(serviceTaskName,result);
                            throw new BpmnError("httpError");
                        } else {
                            throw new RuntimeException("服务调用出错,返回结果:"+response.getBodyText());
                        }
                        //throw new BpmnError("httpError",response.getBodyText());
                    } else {
                        serviceTaskRes result=new serviceTaskRes();
                        result.setStatus(true);
                        result.setBody(response.getBodyText());
                        cachedServiceTaskResult.getServiceTaskRes(oid).put(serviceTaskName,result);
                    }
                    commandContext.setLastResponse((response.getBodyText()));
                    commandContext.storeOutput(execution.getCurrentFlowElement().getName(), simpleJsonParser.parse(response.getBodyText()));


                    //对齐output
                    if (output!=null) {
                        //如果有指定输出
                        String outputString=inputSelectParser.parse(dataPool, output.getValue(execution).toString());
                        commandContext.setLastResponse(outputString);
                    }
                    //流程变量设置
                    if (processVariable!=null) {
                        Map<String,Object> variables=jsonTransfer.jsonToMap(inputSelectParser.parse(dataPool, processVariable.getValue(execution).toString()));
                        execution.setVariables(variables);
                    }
                } else {
                    serviceTaskRes serviceTaskRes= serviceTaskResMap.get(serviceTaskName);
                    if (serviceTaskRes==null) {
                        throw new RuntimeException("未检测到"+serviceTaskName+"的服务任务的执行结果");
                    }
                    if (!serviceTaskRes.isStatus()) {
                        throw new BpmnError("httpError");
                    }
                }
            }

            /*
             * 判断是否要开启或跳转至其他流程
             */
            RuntimeService runtimeService = ActivitiUtils.runtimeService;
            if (nextMessage!=null) {
                //表明信息流流向intermidateMessageCatchEvent
                String messageName=nextMessage.getValue(execution).toString();
                runtimeService.messageEventReceived(messageName, oidEvents.getEventExecutionIdByOidAndName(oid,messageName));
            }
            if (nextProcess!=null) {
                //表明信息流流向startMessageEvent
                String processDefinitionKey=nextProcess.getValue(execution).toString();
                runtimeService.startProcessInstanceById(cachedData.getProcessId(processDefinitionKey,oid));
            }
            // variables.put("have", "true");
            // variables =
            // ActivitiUtils.addVariables(JSON.parseObject(response.getBodyText()),
            // variables);
            // runtimeService.setVariables(execution.getId(), variables);
        } catch (Exception e) {
            if (e instanceof BpmnError) throw new BpmnError("httpError");
            else throw new RuntimeException("serviceTask:"+execution.getCurrentFlowElement().getName()+"执行失败,失败原因:"+e.getMessage());
        }
    }
}