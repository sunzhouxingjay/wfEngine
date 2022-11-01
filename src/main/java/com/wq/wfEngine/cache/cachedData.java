package com.wq.wfEngine.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.impl.db.redisEntity.cachedResponse;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;

import com.alibaba.fastjson.JSON;
import com.wq.wfEngine.activiti.workflowFunction;

public class cachedData {
    private static volatile ConcurrentHashMap<String,ConcurrentHashMap<String,String>> currentTaskNameId =new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String,cachedResponse> cachedWorkflowResponse=new ConcurrentHashMap<>();

    //用于缓存一些会经常访问到的数据
    private static volatile ConcurrentHashMap<String,String> processKeyToId=new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String,String> deploymentNameToMainProcessDefinitionId=new ConcurrentHashMap<>();

    //存储workflowResponse,用于等待flush时，更新currentTaskNameId
    public static void storeWorkflowResponse(cachedResponse response,String Oid) {
        cachedWorkflowResponse.put(Oid,response);
    }

    public static String getProcessId(String processKey) {
        return processKeyToId.get(processKey);
    }

    //用于instance和complete时更新对应实例的当前状态
    public static void updateCurrentTaskStatus(String Oid) {
        cachedResponse response=cachedWorkflowResponse.remove(Oid);
        //如果是deploy则无须更新对应状态
        if (response.isDeploy()) return;
        //若toTasks不为空，则需要处理
        //没有对应的Oid,则创建对应的hashMap
        if (response.getToTasks()!=null) {
            if (!currentTaskNameId.containsKey(Oid)) {
                currentTaskNameId.put(Oid,new ConcurrentHashMap<String,String>());
            }
            currentTaskNameId.get(Oid).putAll(response.getToTasks());
            //System.out.println(currentTaskNameId.toString());
        }
        //再进行删除fromTask,需要进行判空
        Map<String,String> fromTask=response.getFromTask();
        if (fromTask!=null) {
            for (String Name:fromTask.keySet()) {
                currentTaskNameId.get(Oid).remove(Name);
            }
        }
        //删除后，判空，如果对应的Oid的Map为空，即认为该Oid对应的实例已执行完成
        if (currentTaskNameId.get(Oid).isEmpty()) {
            currentTaskNameId.remove(Oid);
        }
    }

    public static String getMainProcessId(String fileName) {
        if (!deploymentNameToMainProcessDefinitionId.containsKey(fileName)) {
            Deployment deployment = workflowFunction.repositoryService.createDeploymentQuery().deploymentName(fileName).singleResult();
            String deploymentId = deployment.getId();
            List<ProcessDefinition> processDefinitionList = workflowFunction.repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deploymentId).list();
            BpmnModel bpmnModel = workflowFunction.repositoryService.getBpmnModel(processDefinitionList.get(0).getId());
            //拿到processKey
            String mainProcessKey = bpmnModel.getMainProcess().getId();
            for (ProcessDefinition processDefinition:processDefinitionList) {
                processKeyToId.put(processDefinition.getId().split(":")[0],processDefinition.getId());
                // if (processDefinition.getId().split(":")[0].equals(mainProcessKey)) {
                //     mainProcessId=processDefinition.getId();
                //     break;
                // }
            }
            deploymentNameToMainProcessDefinitionId.put(fileName,processKeyToId.get(mainProcessKey));
        }
        return deploymentNameToMainProcessDefinitionId.get(fileName);
    }

    public static String getTaskId(String Oid,String taskName) {
        return currentTaskNameId.get(Oid).get(taskName);
    }

}
