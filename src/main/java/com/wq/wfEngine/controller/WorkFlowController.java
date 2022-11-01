package com.wq.wfEngine.controller;

import com.alibaba.fastjson.JSON;
import com.wq.wfEngine.entity.NetWorkRes;
import com.wq.wfEngine.entity.WfOperation;
import com.wq.wfEngine.entity.WfTask;
import com.wq.wfEngine.activiti.ActivitiUtils;
import com.wq.wfEngine.activiti.RsaEncrypt;
import com.wq.wfEngine.activiti.workflowFunction;
import com.wq.wfEngine.cache.jsonTransfer;
import com.wq.wfEngine.pojo.flowNode;

import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

@Controller
public class WorkFlowController {
    
    ProcessEngine processEngine = ActivitiUtils.processEngine;
    RepositoryService repositoryService = ActivitiUtils.repositoryService;
    TaskService taskService = ActivitiUtils.taskService;
    RuntimeService runtimeService = ActivitiUtils.runtimeService;
    HistoryService historyService = ActivitiUtils.historyService;
    RsaEncrypt rsaEncrypt = new RsaEncrypt();
    HashSet<String> taskStatus = new HashSet<>();
    // 对于请求服务的任务，taskid若存在于set内则可以完成，若不存在则处于不可完成的状态，如服务未执行
    // HashMap<String,HashMap<String,String>> Variables=new HashMap<>();
    Map<String,Map<String,flowNode>> deployment_flowMap=new HashMap<>();//BPMN model activitiID到flowNode的映射
    Map<String,Map<String,Set<String>>> deployment_activityOutFlows=new HashMap<>();//BPMN model activiti的outflows
    Map<String,Map<String,String>> deployment_activityNametoId=new HashMap<>();//对应filenName的BPMNmodel ACTIVITI Name到ID的映射
    Map<String,String> deployment_NametoId=new HashMap<>();//fileName到deploymentId的映射
    Map<String,String> deployment_IdtoMainProcessId=new HashMap<>();//deploymentId到BPMN mainProcessId的映射
    String redisInitStatus=ActivitiUtils.InitRedis();
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    // private int count=0;
    // httpclient连接池

    @RequestMapping(value = "/hello", method = {RequestMethod.GET,RequestMethod.POST})
    @ResponseBody
    public String hello() {
        return "hello";
    }

    @RequestMapping(value="/getTaskIds",method = {RequestMethod.POST,RequestMethod.GET})
    @ResponseBody
    public String getTaskIds() throws IOException {
        Set<String> taskIds= stringRedisTemplate.keys("t-"+"*");
        File taskidCsv=new File("/home/sunweekstar/sunzhouxing/taskid.csv");
        if (!taskidCsv.exists()) {
            taskidCsv.createNewFile();
        }
        Writer writer=new FileWriter(taskidCsv, true);
        for (String taskid:taskIds) {
            writer.write(taskid.split("-")[1]+"\n");
        }
        writer.close();
        return "ok";
    }


    // 测试
    @RequestMapping(value = "/test", method = {RequestMethod.POST,RequestMethod.GET})
    @ResponseBody
    public String test() {
        // Map<Integer,Integer> map=new HashMap<>();
        // for (int i=0;i<20;i++) {
        //     map.put(i,i);
        // }
        // stringRedisTemplate.opsForValue().set("key","rO0ABXNyAD9vcmcuYWN0aXZpdGkuZW5naW5lLmltcGwucGVyc2lzdGVuY2UuZW50aXR5LkV4ZWN1dGlvbkVudGl0eUltcGwAAAAAAAAAAQIAJUkAEmRlYWRMZXR0ZXJKb2JDb3VudEkAFmV2ZW50U3Vic2NyaXB0aW9uQ291bnRJABFpZGVudGl0eUxpbmtDb3VudFoACGlzQWN0aXZlWgAMaXNDb25jdXJyZW50WgAOaXNDb3VudEVuYWJsZWRaAAxpc0V2ZW50U2NvcGVaABNpc011bHRpSW5zdGFuY2VSb290WgAHaXNTY29wZUkACGpvYkNvdW50SQARc3VzcGVuZGVkSm9iQ291bnRJAA9zdXNwZW5zaW9uU3RhdGVJAAl0YXNrQ291bnRJAA10aW1lckpvYkNvdW50SQANdmFyaWFibGVDb3VudEwACmFjdGl2aXR5SWR0ABJMamF2YS9sYW5nL1N0cmluZztMAAxhY3Rpdml0eU5hbWVxAH4AAUwAC2J1c2luZXNzS2V5cQB+AAFMAAxkZWxldGVSZWFzb25xAH4AAUwADGRlcGxveW1lbnRJZHEAfgABTAALZGVzY3JpcHRpb25xAH4AAUwACWV2ZW50TmFtZXEAfgABTAAIbG9ja1RpbWV0ABBMamF2YS91dGlsL0RhdGU7TAAEbmFtZXEAfgABTAAIcGFyZW50SWRxAH4AAUwAF3BhcmVudFByb2Nlc3NJbnN0YW5jZUlkcQB+AAFMABNwcm9jZXNzRGVmaW5pdGlvbklkcQB+AAFMABRwcm9jZXNzRGVmaW5pdGlvbktleXEAfgABTAAVcHJvY2Vzc0RlZmluaXRpb25OYW1lcQB+AAFMABhwcm9jZXNzRGVmaW5pdGlvblZlcnNpb250ABNMamF2YS9sYW5nL0ludGVnZXI7TAARcHJvY2Vzc0luc3RhbmNlSWRxAH4AAUwADnF1ZXJ5VmFyaWFibGVzdAAQTGphdmEvdXRpbC9MaXN0O0wAFXJvb3RQcm9jZXNzSW5zdGFuY2VJZHEAfgABTAAJc3RhcnRUaW1lcQB+AAJMAAtzdGFydFVzZXJJZHEAfgABTAAQc3VwZXJFeGVjdXRpb25JZHEAfgABTAAIdGVuYW50SWRxAH4AAXhyAD1vcmcuYWN0aXZpdGkuZW5naW5lLmltcGwucGVyc2lzdGVuY2UuZW50aXR5LlZhcmlhYmxlU2NvcGVJbXBsAAAAAAAAAAECAAFMABJ1c2VkVmFyaWFibGVzQ2FjaGV0AA9MamF2YS91dGlsL01hcDt4cgA6b3JnLmFjdGl2aXRpLmVuZ2luZS5pbXBsLnBlcnNpc3RlbmNlLmVudGl0eS5BYnN0cmFjdEVudGl0eQAAAAAAAAABAgACSQAIcmV2aXNpb25MAAJpZHEAfgABeHAAAAABdAAHNjcyNzE4NXNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAB3CAAAABAAAAAAeAAAAAAAAAAAAAAAAAEAAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAHBwcHBwcHBwdAAdZmlsZTpsb29wLmJwbW4/NTM2MzMwMDA1MjUyNDJwcHQAE1Byb2Nlc3NfMTo2OjY1NTUwMDN0AAlQcm9jZXNzXzFwc3IAEWphdmEubGFuZy5JbnRlZ2VyEuKgpPeBhzgCAAFJAAV2YWx1ZXhyABBqYXZhLmxhbmcuTnVtYmVyhqyVHQuU4IsCAAB4cAAAAAZxAH4ACXBxAH4ACXNyAA5qYXZhLnV0aWwuRGF0ZWhqgQFLWXQZAwAAeHB3CAAAAYEfJQvleHBwdAAA");
        // for (int i=0;i<7;i++) {
        //     stringRedisTemplate.opsForValue().get("key");
        // }
        
        return "ok";
    }



    @CrossOrigin
    @RequestMapping(value = "/wfDeploy", method = RequestMethod.POST)
    @ResponseBody
    public String wfDeploy(@RequestBody String req) {
        Map<String,Object> requestMap=jsonTransfer.jsonToMap(req);
        String fileName=String.valueOf(requestMap.get("fileName"));
        String fileContent=String.valueOf(requestMap.get("fileContent"));
        //System.out.println("fileName:"+fileName);
        //System.out.println("fileContent:"+fileContent);
        return workflowFunction.deploy(fileName, fileContent);
    }

    @CrossOrigin
    @RequestMapping(value = "/wfInstance/{Oid}",method = RequestMethod.POST)
    @ResponseBody
    public String wfInstance(@RequestBody String req,@PathVariable String Oid) {
        Map<String,Object> requestMap=jsonTransfer.jsonToMap(req);
        String fileName=String.valueOf(requestMap.get("fileName"));
        String businessData=String.valueOf(requestMap.get("businessData"));
        //System.out.println(fileName);
        //System.out.println(Oid);
        return workflowFunction.instance(fileName, Oid,businessData);
    }

    @CrossOrigin
    @RequestMapping(value = "/wfComplete/{Oid}",method = RequestMethod.POST)
    @ResponseBody
    public String wfComplete(@RequestBody String req,@PathVariable String Oid) {
        Map<String,Object> requestMap=jsonTransfer.jsonToMap(req);
        String taskName=String.valueOf(requestMap.get("taskName"));
        String processData=String.valueOf(requestMap.get("processData"));
        String businessData=String.valueOf(requestMap.get("businessData"));
        return workflowFunction.complete(Oid, taskName, processData, businessData);
    }


    @CrossOrigin
    @RequestMapping(value = "/flush",method = RequestMethod.POST)
    @ResponseBody
    public String flush(@RequestBody String req) {
        Map<String,Object> requestMap=jsonTransfer.jsonToMap(req);
        String[] Oids=String.valueOf(requestMap.get("oidsString")).split("\\|");
        workflowFunction.flush(Oids);
        return "ok";
    }


    @CrossOrigin
    @RequestMapping(value = "/testwfDeploy", method = RequestMethod.POST)
    @ResponseBody
    public String testwfDeploy(String fileName, String fileContent) {
        NetWorkRes netWorkRes = new NetWorkRes();
        try {
            List<Deployment> deploymentList = repositoryService.createDeploymentQuery().deploymentName(fileName).list();
            for (Deployment d : deploymentList) {
                repositoryService.deleteDeployment(d.getId(), true);// 默认是false true就是级联删除
            }

            Deployment deployment = repositoryService.createDeployment()// 创建Deployment对象
                    .addString(fileName, fileContent)
                    .name(fileName)
                    .deploy();
            String deploymentId=deployment.getId();
            deployment_NametoId.put(fileName,deploymentId);//将deploymentId这种简单映射加入到hashMap中
            deployment_flowMap.put(fileName,ActivitiUtils.initDeployment_FlowMap(deploymentId));
            deployment_activityOutFlows.put(fileName,ActivitiUtils.initDeployment_activityOutFlows(deploymentId));
            deployment_activityNametoId.put(fileName,ActivitiUtils.initDeployment_activityNametoId(deploymentId));
            List<ProcessDefinition> processDefinitionList = repositoryService.createProcessDefinitionQuery()
            .deploymentId(deploymentId).list();
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionList.get(0).getId());
            Process mainProcess = bpmnModel.getMainProcess();
            deployment_IdtoMainProcessId.put(deploymentId,mainProcess.getId());

            Map<String, String> temp = new HashMap<>();
            temp.put("fileName", fileName);
            netWorkRes.setCode("200");
            netWorkRes.setMsg("success");
            netWorkRes.setData(JSON.toJSONString(temp));
            return JSON.toJSONString(netWorkRes);
        } catch (Exception e) {
            ActivitiUtils.clean(fileName);
            deployment_NametoId.remove(fileName);//失败清除Map
            netWorkRes.setCode("500");
            netWorkRes.setMsg("error");
            netWorkRes.setData(e.getMessage());
            return JSON.toJSONString(netWorkRes);
        }
    }

    @CrossOrigin
    @RequestMapping(value = "/testwfInstance", method = RequestMethod.POST)
    @ResponseBody
    public String testwfInstance(HttpServletRequest req) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(req.getInputStream()));
        StringBuffer sb = new StringBuffer();
        String s = null;
        while ((s = br.readLine()) != null) {
            sb.append(s);
        }
        Map<String, Object> request_Map = JSON.parseObject(sb.toString());
        String fileName = request_Map.get("fileName").toString();
        String insName = request_Map.get("insName").toString()+"?"+String.valueOf(System.nanoTime());//
        NetWorkRes netWorkRes = new NetWorkRes();
        try {


            Deployment deployment = repositoryService.createDeploymentQuery().deploymentName(fileName).singleResult();
            String deploymentId = deployment.getId();
            List<ProcessDefinition> processDefinitionList = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deploymentId).list();
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionList.get(0).getId());
            Process mainProcess = bpmnModel.getMainProcess();
            String mainProcessId=mainProcess.getId();
            Map<String, Object> variables = new HashMap<>();
            variables.put("insName", insName);
            ProcessInstance mainProcessInstance = runtimeService.startProcessInstanceByKey(mainProcessId,
                    variables);
            if (!mainProcessInstance.isEnded()) {
                runtimeService.setProcessInstanceName(mainProcessInstance.getId(), insName);
            }
            return "ok";

        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @RequestMapping(value = "/testwftaskquery", method = RequestMethod.POST)
    @ResponseBody
    public String testwftaskquery(String insName, String operation, String varname, String priString, String cert,
            String serviceArgs) {
        NetWorkRes netWorkRes = new NetWorkRes();
        try {
            List<ProcessInstance> processInstanceList = runtimeService.createProcessInstanceQuery()
                    .processInstanceNameLike(insName + "%").list();
            for (ProcessInstance processInstance : processInstanceList) {
                List<Task> taskList = taskService.createTaskQuery().processInstanceId(processInstance.getId())
                        .taskName(operation).list();
                for (Task task : taskList) {
                    String value = runtimeService.getVariable(task.getExecutionId(), varname, String.class);
                    if (value != null) {
                        netWorkRes.setCode("200");
                        netWorkRes.setMsg("success");
                        ;
                        netWorkRes.setData(value);
                        ;
                        return JSON.toJSONString(netWorkRes);
                    }
                }
            }
            netWorkRes.setCode("500");
            netWorkRes.setMsg("error");
            netWorkRes.setData("无对应变量");
            return JSON.toJSONString(netWorkRes);
        } catch (Exception e) {
            netWorkRes.setCode("500");
            netWorkRes.setMsg("error");
            netWorkRes.setData(e.getMessage());
            return JSON.toJSONString(netWorkRes);
        }
    }

 
    @CrossOrigin
    @RequestMapping(value="/completeByTaskid",method = RequestMethod.POST)
    @ResponseBody
    public String completeByTaskid (HttpServletRequest req) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(req.getInputStream()));
        StringBuffer sb = new StringBuffer();
        String s = null;
        while ((s = br.readLine()) != null) {
            sb.append(s);
        }
        Map<String, Object> request_Map = JSON.parseObject(sb.toString());
        String taskId=request_Map.get("taskId").toString();
        String formVal=request_Map.get("formVal").toString();
        NetWorkRes netWorkRes=new NetWorkRes();
        try {
            taskService.complete(taskId,JSON.parseObject(formVal));
            return "ok";
            // count++;
            // if (count>=10000) {
            //     count=0;
            //     runtimeService.DeleteFormRedis();
            // }
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @CrossOrigin
    @RequestMapping(value = "/testwfComplete", method = RequestMethod.POST)
    @ResponseBody
    // formval从[]list变为{}map
    public String testwfComplete(HttpServletRequest req) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(req.getInputStream()));
        StringBuffer sb = new StringBuffer();
        String s = null;
        while ((s = br.readLine()) != null) {
            sb.append(s);
        }
        Map<String, Object> request_Map = JSON.parseObject(sb.toString());
        String insName = request_Map.get("insName").toString();
        String operation = request_Map.get("operation").toString();
        String formVal = request_Map.get("formVal").toString();
        String priString = request_Map.get("priString").toString();
        String cert = request_Map.get("cert").toString();
        String serviceArgs = request_Map.get("serviceArgs").toString();
        NetWorkRes netWorkRes=new NetWorkRes();
        try {

            List<ProcessInstance> processInstanceList = runtimeService.createProcessInstanceQuery()
                    .processInstanceNameLike(insName + "%").list();//todo:加redis

            WfTask operationTask = new WfTask();
            int taskamount = 0;
            for (ProcessInstance processInstance : processInstanceList) {
                //System.out.println(processInstance.getId());// 6666666666666666666666666
                List<Task> taskList = taskService.createTaskQuery().processInstanceId(processInstance.getId())
                        .taskName(operation)
                        .list();//todo:加redis
                taskamount += taskList.size();
                for (Task t : taskList) {
                    Map<String, Object> variables = ActivitiUtils.addVariables(JSON.parseObject(formVal),
                            taskService.getVariables(t.getId()));
                    taskService.setVariables(t.getId(), variables);

                    operationTask = ActivitiUtils.taskToWfTask(t);
                    if (operationTask.isRequestNode()) {
                        taskService.complete(t.getId());
                        // if (!taskStatus.contains(t.getId())) {
                        // netWorkRes.put("code", 500);
                        // netWorkRes.put("Response", "当前任务服务未执行完成，处于不可完成状态");
                        // return JSON.toJSONString(netWorkRes);
                        // }
                        Map<String, String> messageStrMap = ActivitiUtils.getTaskMessageTarget(t);
                        for (String messageStr : messageStrMap.keySet()) {
                            if (messageStrMap.get(messageStr).equals("StartEvent")) {
                                variables.put("insName", insName);
                                ProcessInstance subProcessInstance = runtimeService
                                        .startProcessInstanceByKey(messageStr, variables);
                                if (!subProcessInstance.isEnded()) {
                                    runtimeService.setProcessInstanceName(subProcessInstance.getId(),
                                            insName + ':' + subProcessInstance.getProcessDefinitionKey());
                                }
                            } else if (messageStrMap.get(messageStr).equals("IntermediateCatchEvent")) {
                                for (ProcessInstance subProcessInstance : processInstanceList) {
                                    List<Execution> executions = runtimeService.createExecutionQuery()
                                            .messageEventSubscriptionName(messageStr)
                                            .processInstanceId(subProcessInstance.getId())
                                            .list();
                                    for (Execution execution : executions) {
                                        if (execution != null) {
                                            runtimeService.messageEventReceived(messageStr, execution.getId(),
                                                    variables);
                                        }
                                    }
                                }
                            }
                        }
                        // taskService.complete(t.getId());
                        // taskStatus.remove(t.getId());
                        // 任务完成，从hashset中去掉taskid
                        // taskService.complete(t.getId(), variables);
                    } else {
                        Map<String, String> messageStrMap = ActivitiUtils.getTaskMessageTarget(t);
                        //System.out.println(messageStrMap.toString());
                        taskService.complete(t.getId());
                        for (String messageStr : messageStrMap.keySet()) {
                            if (messageStrMap.get(messageStr).equals("StartEvent")) {
                                variables.put("insName", insName);

                                ProcessInstance subProcessInstance = runtimeService
                                        .startProcessInstanceByKey(messageStr, variables);
                                if (!subProcessInstance.isEnded()) {
                                    runtimeService.setProcessInstanceName(subProcessInstance.getId(),
                                            insName + ':' + subProcessInstance.getProcessDefinitionKey());
                                    WfOperation wfOperation = new WfOperation();
                                    wfOperation.setType("StartEvent");
                                    wfOperation.setKey(subProcessInstance.getProcessDefinitionKey());
                                }
                            } else if (messageStrMap.get(messageStr).equals("IntermediateCatchEvent")) {
                                for (ProcessInstance subProcessInstance : processInstanceList) {
                                    List<Execution> executions = runtimeService.createExecutionQuery()
                                            .messageEventSubscriptionName(messageStr)
                                            .processInstanceId(subProcessInstance.getId())
                                            .list();
                                    for (Execution execution : executions) {
                                        if (execution != null) {
                                            runtimeService.messageEventReceived(messageStr, execution.getId(),
                                                    variables);
                                            WfOperation wfOperation = new WfOperation();
                                            wfOperation.setType("MessageIntermediateCatchEvent");
                                            wfOperation.setName(messageStr);
                                        }
                                    }
                                }
                            }
                        }
                        // taskService.complete(t.getId());
                        // taskService.complete(t.getId(), variables);
                    }
                }
            }
            if (taskamount == 0) {
                netWorkRes.setCode("500");
                netWorkRes.setData("没有对应的任务节点，确认参数是否正确");
                netWorkRes.setMsg("error");
                return JSON.toJSONString(netWorkRes);
            }
            /**
             * 下方注释是查询nextTask的一个过程
             * 暂时把这个过程注释掉
             * 查询使用前端去查询
             */
            // List<Map<String, String>> nextTasks = new LinkedList<>();
            // processInstanceList = runtimeService.createProcessInstanceQuery().processInstanceNameLike(insName +
            //         "%").list();
            // for (ProcessInstance processInstance : processInstanceList) {
            //     List<Task> nextTaskList = taskService.createTaskQuery()
            //             .processInstanceId(processInstance.getProcessInstanceId()).list();
            //     for (Task task : nextTaskList) {
            //         Map<String, String> temp = new HashMap<>();
            //         //temp.put("taskid", task.getId());
            //         temp.put("taskName", task.getName());
            //         Map<String,Object> processVariables=taskService.getVariables(task.getId());
            //         processVariables.remove("bussiness_data");
            //         temp.put("process_variables",JSON.toJSONString(processVariables));
            //         temp.put("bussiness_data",taskService.getVariable(task.getId(), "bussiness_data", String.class));
            //         nextTasks.add(temp);
            //     }
            // }

            // netWorkRes.setCode("200");
            // Map<String, Object> response = new HashMap<>();
            // response.put("insName", insName);
            // response.put("nextTaskList", nextTasks);
            // netWorkRes.setData(JSON.toJSONString(response));
            // netWorkRes.setMsg("success");
            // return JSON.toJSONString(netWorkRes);
            netWorkRes.setCode("200");
            netWorkRes.setMsg("success");
            return JSON.toJSONString(netWorkRes);
        } catch (Exception e) {
            netWorkRes.setCode("500");
            netWorkRes.setData(e.getMessage());
            netWorkRes.setMsg("error");
            return JSON.toJSONString(netWorkRes);
        }
    }


    /*
     * @PostMapping(path = "/activitiOperate")
     * 
     * @ResponseBody
     * public String activitiOperate(String fileName,String fileContent,String
     * history,String operateUser,String operation,String formVal){
     * ActivitiUtils.init(fileName,fileContent);
     * List<FbTask> fbTaskList = JSONObject.parseArray(history,FbTask.class);
     * Map<String, Object> variables=null;
     * TaskQuery taskQuery=null;
     * for(FbTask fbTask:fbTaskList){
     * //创建查询对象
     * taskQuery= taskService.createTaskQuery();
     * List<Task> taskList = taskQuery.list();
     * for(Task t:taskList){
     * variables =
     * ActivitiUtils.setVariables(fbTask.nonJsonGetFormPropertiesList());
     * if(t.getName().equals(fbTask.getName())){
     * taskService.complete(t.getId(),variables);
     * }
     * }
     * }
     * variables=null;
     * taskQuery= taskService.createTaskQuery();
     * //设置查询条件
     * //taskQuery.taskAssignee(operateUser);
     * //指定流程定义key，只查询某个流程的任务
     * //taskQuery.processDefinitionKey(processDefinitionKey);
     * //获取查询列表
     * 
     * List<Task> taskList = taskQuery.list();
     * List<FormProperty> formValList = JSON.parseArray(formVal,FormProperty.class);
     * for(Task t:taskList){
     * variables=ActivitiUtils.setVariables(formValList);
     * if(t.getName().equals(operation)){
     * taskService.complete(t.getId(),variables);
     * }
     * }
     * List<FbTask> nextFbTaskList = ActivitiUtils.getFbTaskList();
     * ActivitiUtils.unifyAssignee(nextFbTaskList, fbTaskList);
     * FbResultDTO resultDTO = new FbResultDTO();
     * resultDTO.setCode("200");
     * resultDTO.setData(nextFbTaskList);
     * resultDTO.setMsg("success");
     * 
     * return JSON.toJSONString(resultDTO);
     * }
     * 
     * @PostMapping(path = "/op")
     * 
     * @ResponseBody
     * public String op(String fileName){
     * List<Deployment> deploymentList =
     * repositoryService.createDeploymentQuery().list();
     * for(Deployment d:deploymentList){
     * repositoryService.deleteDeployment(d.getId(), true);// 默认是false true就是级联删除
     * }
     * 
     * processEngine.getRepositoryService()//获取流程定义和部署对象相关的Service
     * .createDeployment()//创建部署对象
     * .name("EnterCountry")//声明流程的名称
     * .addClasspathResource("EnterCountry.bpmn")//加载资源文件，一次只能加载一个文件
     * .deploy();
     * RuntimeService runtimeService = processEngine.getRuntimeService();
     * ProcessInstance processInstance =
     * runtimeService.startProcessInstanceByKey("EnterCountry");
     * TaskQuery taskQuery= taskService.createTaskQuery();
     * List<Task> taskList = taskQuery.list();
     * for(Task t:taskList) {
     * taskService.complete(t.getId());
     * }
     * taskQuery= taskService.createTaskQuery();
     * List<Task> taskList1 = taskQuery.list();
     * for(Task t:taskList1) {
     * t.getId();
     * UserTask userTask
     * =(UserTask)repositoryService.getBpmnModel(t.getProcessDefinitionId()).
     * getFlowElement(t.getTaskDefinitionKey());
     * t.getId();
     * 
     * }
     * List<FbTask> nextFbTaskList = ActivitiUtils.getFbTaskList();
     * FbResultDTO resultDTO = new FbResultDTO();
     * resultDTO.setCode("200");
     * resultDTO.setData(nextFbTaskList);
     * resultDTO.setMsg("success");
     * 
     * return JSON.toJSONString(resultDTO);
     * }
     */

    /*
     * String processDefinitionId = task.getProcessDefinitionId();
     * BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
     * String lastActivityId = null;
     * List<HistoricActivityInstance> haiFinishedList =
     * historyService.createHistoricActivityInstanceQuery()
     * .executionId(lastExecutionId).finished().list();
     * for (
     * HistoricActivityInstance hai : haiFinishedList) {
     * if (lastTaskId.equals(hai.getTaskId())) {
     * // 得到ActivityId，只有HistoricActivityInstance对象里才有此方法
     * lastActivityId = hai.getActivityId();
     * break;
     * }
     * }
     * // 得到上个节点的信息
     * FlowNode lastFlowNode = (FlowNode)
     * bpmnModel.getMainProcess().getFlowElement(lastActivityId);
     * List<SequenceFlow> incomingFlows = lastFlowNode.getIncomingFlows();
     * List<SequenceFlow> outgoingFlows = lastFlowNode.getOutgoingFlows();
     */
}
