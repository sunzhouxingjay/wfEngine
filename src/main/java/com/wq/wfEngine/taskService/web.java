package com.wq.wfEngine.taskService;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import com.wq.wfEngine.activiti.ActivitiUtils;
import com.wq.wfEngine.cache.cachedData;
import com.wq.wfEngine.netutils.Connect;

import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.db.cache.oidEvents;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;

public class web implements JavaDelegate {
    private Expression dataUrl;
    private Expression serviceUrl;
    private Expression nextProcess;
    private Expression nextMessage;

    public void execute(DelegateExecution execution) {
        try {
            //拿变量
            CommandContext commandContext= Context.getCommandContext();
            if (serviceUrl!=null) {
                //表明要请求服务
                String businessData=commandContext.getBusinessData();
                Future<SimpleHttpResponse> future = Connect.doPost(serviceUrl.getValue(execution).toString(), businessData);
                SimpleHttpResponse response = future.get();
                /*
                 * 一个线程里如果同时只有一个activiti的命令在运行，则上下文栈里只有唯一一个上下文
                 * 可以通过增加上下文中的属性，对属性进行改写来达到一个数据传递的作用
                 * 这里改写的是业务数据
                 * 将返回的数据，保存为业务数据
                 */
                commandContext.setBusinessData(response.getBodyText());
            }

            /*
             * 判断是否要开启或跳转至其他流程
             */
            RuntimeService runtimeService = ActivitiUtils.runtimeService;
            if (nextMessage!=null) {
                //表明信息流流向intermidateMessageCatchEvent
                String messageName=nextMessage.getValue(execution).toString();
                String oid=commandContext.getOid();
                Map<String,Object> variables=new HashMap<String,Object>(){{
                    put("Oid",oid);
                }};//暂时没有放进去变量感觉并不需要
                runtimeService.messageEventReceived(messageName, oidEvents.getEventExecutionIdByOidAndName(oid,messageName), variables);
            }
            if (nextProcess!=null) {
                //表明信息流流向startMessageEvent
                String processDefinitionKey=nextProcess.getValue(execution).toString();
                String oid=commandContext.getOid();
                Map<String,Object> variables=new HashMap<String,Object>(){{
                    put("Oid",oid);
                }};//暂时没有放进去变量感觉并不需要
                runtimeService.startProcessInstanceById(cachedData.getProcessId(processDefinitionKey),variables);
            }
            // variables.put("have", "true");
            // variables =
            // ActivitiUtils.addVariables(JSON.parseObject(response.getBodyText()),
            // variables);
            // runtimeService.setVariables(execution.getId(), variables);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}