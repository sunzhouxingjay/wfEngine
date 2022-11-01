package com.wq.wfEngine.activiti;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.impl.db.redisEntity.*;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.task.Task;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.wq.wfEngine.cache.cachedData;
import com.wq.wfEngine.cache.jsonTransfer;


public class workflowFunction {
    public static ProcessEngine processEngine = ActivitiUtils.processEngine;
    public static RepositoryService repositoryService = ActivitiUtils.repositoryService;
    public static TaskService taskService = ActivitiUtils.taskService;
    public static RuntimeService runtimeService = ActivitiUtils.runtimeService;
    public static HistoryService historyService = ActivitiUtils.historyService;

    //Map<String,Map<String,flowNode>> deployment_flowMap=new HashMap<>();//BPMN model activitiID到flowNode的映射
    Map<String,Map<String,Set<String>>> deployment_activityOutFlows=new HashMap<>();//BPMN model activiti的outflows
    Map<String,Map<String,String>> deployment_activityNametoId=new HashMap<>();//对应filenName的BPMNmodel ACTIVITI Name到ID的映射
    Map<String,String> deployment_NametoId=new HashMap<>();//fileName到deploymentId的映射
    Map<String,String> deployment_IdtoMainProcessId=new HashMap<>();//deploymentId到BPMN mainProcessId的映射
    public static String deploy(String fileName,String fileContent) {
        List<Deployment> deploymentList = repositoryService.createDeploymentQuery().deploymentName(fileName).list();

        for (Deployment d : deploymentList) {
            repositoryService.deleteDeployment(d.getId(), true);// 默认是false true就是级联删除
        }

        Deployment deployment = repositoryService.createDeployment()// 创建Deployment对象
        .addString(fileName, fileContent)
        .name(fileName)
        .deploy();//对于deployment,Name就是他的Oid

        cachedResponse response= runtimeService.getWorkflowResponse(fileName);

        //存至缓存，等待flush时更新，对应实例的状态
        cachedData.storeWorkflowResponse(response, fileName);

        return response.getEncodeString();
        //return response.getResponseString();
    }

    public static String instance(String fileName,String Oid,String businessData) {
        Map<String,Object> variables=new HashMap<String,Object>() {
            {
                put("Oid",Oid);
            };
        };
        if (businessData.length()>2) {
            variables.put("businessData",businessData);
        }
        //拿到需要实例化的第一个流程Id
        String mainProcessId=cachedData.getMainProcessId(fileName);
        //System.out.println(mainProcessId);
        
        //实例化
        runtimeService.startProcessInstanceById(mainProcessId,variables);
        //拿到实例化的workflowResponse
        cachedResponse response=runtimeService.getWorkflowResponse(Oid);
        

        //存至缓存，等待flush时更新，对应实例的状态
        cachedData.storeWorkflowResponse(response, Oid);
        //返回给请求方的数据
        //return response.getResponseString();
        //System.out.println("instance end");
        return response.getEncodeString(); 
    }

    public static String complete(String Oid,String taskName,String processData,String businessData) {
        //System.out.println("Oid:"+Oid);
        //System.out.println("taskName:"+taskName);
        //System.out.println("processData:"+processData);
        //System.out.println("businessData:"+businessData);
        String taskId=cachedData.getTaskId(Oid, taskName);
        Map<String,Object> variables=jsonTransfer.jsonToMap(processData);
        //传入的变量中放入Oid唯一标识
        variables.put("Oid",Oid);
        //如果有businessData则加入variables,因为business传入的是json数据的字符串格式
        //todo:businessData持久化
        if (businessData.length()>2) {
            variables.put("businessData",businessData);
        }
        //先完成complete
        taskService.complete(taskId, variables,false);
        //再判断是否要开始其他的流程//暂时先把启动其他实例放一边
        // Task task=taskService.getTaskById(taskId);//先拿到对应的Task---这个是自己添加的命令
        // Map<String,String> messageStrMap=cachedData.getMessageFlowMap(task.getProcessDefinitionId());
        // if (messageStrMap==null) {
        //     messageStrMap=ActivitiUtils.getTaskMessageTarget(task);
        // }
        // cachedData.addMessageFlowMap(messageStrMap,task.getProcessDefinitionId());

        cachedResponse response=runtimeService.getWorkflowResponse(Oid);

        //存至缓存，等待flush时更新，对应实例的状态
        cachedData.storeWorkflowResponse(response, Oid);

        return response.getEncodeString();
    }

    public static void flush(String[] Oids) {
        runtimeService.flushCache(Oids);
        System.out.println("updateCurrentTaskStatus start___________________________");
        for (String Oid:Oids) {
            cachedData.updateCurrentTaskStatus(Oid);
        }
        System.out.println("updateCurrentTaskStatus end___________________________");
    }
    /**
     * 服务任务还没有适配，还需要进一步扩展
     */
}
