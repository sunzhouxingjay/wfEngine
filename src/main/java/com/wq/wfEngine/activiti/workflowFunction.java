package com.wq.wfEngine.activiti;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.db.cache.allocationTable;
import org.activiti.engine.impl.db.workflowClass.cachedResponse;
import org.activiti.engine.impl.db.workflowClass.randomGenerator;
import org.activiti.engine.impl.db.workflowClass.workflowResponse;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.task.Task;
import org.activiti.image.impl.DefaultProcessDiagramGenerator;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.wq.wfEngine.cache.cachedData;
import com.wq.wfEngine.cache.cachedServiceTaskResult;
import com.wq.wfEngine.config.evilNodeConfig;
import com.wq.wfEngine.tool.jsonTransfer;


public class workflowFunction {
    public static ProcessEngine processEngine = ActivitiUtils.processEngine;
    public static RepositoryService repositoryService = ActivitiUtils.repositoryService;
    public static TaskService taskService = ActivitiUtils.taskService;
    public static RuntimeService runtimeService = ActivitiUtils.runtimeService;
    public static HistoryService historyService = ActivitiUtils.historyService;

    //Map<String,Map<String,flowNode>> deployment_flowMap=new HashMap<>();//BPMN model activitiID到flowNode的映射

    
    public static List<Deployment> getAllDeployments() {
        List<Deployment> deployments=repositoryService.createDeploymentQuery().list();
        return deployments;
    }


    public static String getSvgContent(String deploymentName) throws IOException {
        BpmnModel model=cachedData.getBpmnModelByName(deploymentName);
        if (model!=null&&model.getLocationMap().size()>0) {
            DefaultProcessDiagramGenerator ge = new DefaultProcessDiagramGenerator();
            InputStream in = ge.generateDiagram(model,"宋体", "宋体", "宋体");
            byte[] bytes=new byte[in.available()];
            in.read(bytes);
            in.close();
            return new String(bytes);
        } else {
            throw new RuntimeException("bpmnModel 为空");
        }
    }

    public static void deleteDeploymentByName(String deploymentName) {
        List<Deployment> deploymentList = repositoryService.createDeploymentQuery().deploymentName(deploymentName).list();
        if (deploymentList.size()==0) {
            throw new RuntimeException("there is no deployment named "+deploymentName);
        }
        for (Deployment d : deploymentList) {
            repositoryService.deleteDeployment(d.getId(), true);// 默认是false true就是级联删除
        }

        cachedData.cleanDeploymentByName(deploymentName);
    }

    public static List<String> filterUserTask(List<Deployment> deployments) {
        List<String> deploymentWithNoUserTask=new ArrayList<>();
        for (Deployment deployment:deployments) {
            if (!cachedData.haveUserTask(deployment.getName())) {
                deploymentWithNoUserTask.add(deployment.getName());
            }
        }
        return deploymentWithNoUserTask;
    }
    //过滤非dbapi
    public static List<String> filterNoDBAPI(List<Deployment> deployments) {
        List<String> deploymentDBAPI=new ArrayList<>();
        for (Deployment deployment:deployments) {
            if (cachedData.isDBAPI(deployment.getName())) {
                deploymentDBAPI.add(deployment.getName());
            }
        }
        return deploymentDBAPI;
    }

    public static Map<String,Object> getServiceTaskInfo(List<String> deploymentNames) {
        Map<String,Object> serviceTaskInfos=new HashMap<>();
        for (String deploymentName:deploymentNames) {
            serviceTaskInfos.put(deploymentName,cachedData.getServiceTaskInfo(deploymentName));
        }
        return serviceTaskInfos;
    }


    public static String getBytesByDeployment(Deployment deployment) throws IOException {
        InputStream in=repositoryService.getResourceAsStream(deployment.getId(), deployment.getName());
        byte[] bytes=new byte[in.available()];
        in.read(bytes);
        in.close();
        return new String(bytes);
    }

    public static String getBytesByDeploymentName(String deploymentName) throws IOException {
        Deployment deployment=repositoryService.createDeploymentQuery().deploymentName(deploymentName).singleResult();
        if (deployment==null) throw new RuntimeException("there is no deployment with name is "+deploymentName);
        InputStream in=repositoryService.getResourceAsStream(deployment.getId(), deployment.getName());
        byte[] bytes=new byte[in.available()];
        in.read(bytes);
        in.close();
        return new String(bytes);
    }

    public static String deploy(String deploymentName,String fileContent) {
        List<Deployment> deploymentList = repositoryService.createDeploymentQuery().deploymentName(deploymentName).list();

        for (Deployment d : deploymentList) {
            repositoryService.deleteDeployment(d.getId(), true);// 默认是false true就是级联删除
        }

        cachedData.cleanDeploymentByName(deploymentName);

        Deployment deployment = repositoryService.createDeployment()// 创建Deployment对象
        .addString(deploymentName, fileContent)
        .name(deploymentName)
        .deploy();//对于deployment,Name就是他的Oid

        cachedResponse response= runtimeService.getWorkflowResponse(deploymentName);

        //存至缓存，等待flush时更新，对应实例的状态
        cachedData.storeWorkflowResponse(response, deploymentName);

        return response.getEncodeString();
        //return response.getResponseString();
    }


    public static String instance(String deploymentName,String Oid,String businessData,String staticAllocationTable,String serviceTaskResultJson) {
        if (evilNodeConfig.isEvil()) {
            return randomGenerator.getWorkflowResponseString();
        }
        if (staticAllocationTable!=null) {
            //静态分配
            cachedData.staticAllocate(staticAllocationTable, deploymentName, Oid);
        }
        //设置分配表
        allocationTable.setAllocationTable(cachedData.getAllocationTable(Oid));
        Map<String,Object> variables=new HashMap<String,Object>() {
            {
                put("Oid",Oid);
            };
        };
        if (businessData.length()>=2) {
            variables.put("businessData",businessData);
        }
        if (serviceTaskResultJson!=null) {
            variables.put("serviceTaskResultJson",serviceTaskResultJson);
        }
        //拿到需要实例化的第一个流程Id
        String mainProcessId=cachedData.getMainProcessId(deploymentName);
        //System.out.println(mainProcessId);
        //实例化
        runtimeService.startProcessInstanceById(mainProcessId,variables);
        //拿到实例化的workflowResponse
        cachedResponse response=runtimeService.getWorkflowResponse(Oid);
        

        //存至缓存，等待flush时更新，对应实例的状态
        cachedData.storeWorkflowResponse(response, Oid);
        //如果是预先执行的设置serviceTaskResultJson
        response.setServiceTaskResultJson(jsonTransfer.serviceTaskResToJsonString(cachedServiceTaskResult.removeServiceTaskRes(Oid)));
        //清空allocationTable,因为底层是threadLocal
        allocationTable.removeAllocationTable();
        
        //返回给请求方的数据
        //return response.getResponseString();
        //System.out.println("instance end");
        return response.getEncodeString(); 
    }


    public static String complete(String Oid,String taskName,String processData,String businessData,String user,String serviceTaskResultJson) {
        if (evilNodeConfig.isEvil()) {
            return randomGenerator.getWorkflowResponseString();
        }
        //设置分配表
        allocationTable.setAllocationTable(cachedData.getAllocationTable(Oid));
        String taskId=cachedData.getTaskId(Oid, taskName);
        if (taskId==null) {
            throw new RuntimeException("no task named "+taskName+" with oid "+Oid);
        }
        Map<String,Object> variables=jsonTransfer.jsonToMap(processData);
        //传入的变量中放入Oid唯一标识
        variables.put("Oid",Oid);
        variables.put("user",user);
        //如果有businessData则加入variables,因为business传入的是json数据的字符串格式
        //todo:businessData持久化
        if (businessData.length()>=2) {
            variables.put("businessData",businessData);
        }
        if (serviceTaskResultJson!=null) {
            variables.put("serviceTaskResultJson",serviceTaskResultJson);
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
        //如果是预先执行的设置serviceTaskResultJson
        response.setServiceTaskResultJson(jsonTransfer.serviceTaskResToJsonString(cachedServiceTaskResult.removeServiceTaskRes(Oid)));

        //清空allocationTable
        allocationTable.removeAllocationTable();

        return response.getEncodeString();
    }

    public static void flush(String[] Oids) {
        try {
            runtimeService.flushCache(Oids);
            for (String Oid:Oids) {
                cachedData.updateCurrentTaskStatus(Oid);
            } 
        } catch (Exception e) {
            for (StackTraceElement element:e.getStackTrace()) {
                System.out.println(element.toString());
            }
            System.out.println(e.getMessage());
            System.out.println(e.getCause().getMessage());
            throw e;
        }
        //runtimeService.flushCache(Oids);
        // for (String Oid:Oids) {
        //     cachedData.updateCurrentTaskStatus(Oid);
        // }
    }
    /**
     * 服务任务还没有适配，还需要进一步扩展
     */
}
