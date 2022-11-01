package com.wq.wfEngine.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wq.wfEngine.entity.InsRet;
import com.wq.wfEngine.entity.WfOperation;
import com.wq.wfEngine.entity.WfResultDTO;
import com.wq.wfEngine.entity.WfTask;
import com.wq.wfEngine.activiti.ActivitiUtils;
import com.wq.wfEngine.activiti.RsaEncrypt;
import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;

@Controller
public class WorkFlowController {
    ProcessEngine processEngine = ActivitiUtils.processEngine;
    RepositoryService repositoryService = ActivitiUtils.repositoryService;
    TaskService taskService = ActivitiUtils.taskService;
    RuntimeService runtimeService = ActivitiUtils.runtimeService;
    RsaEncrypt rsaEncrypt = new RsaEncrypt();

    @RequestMapping(value = "/", method = RequestMethod.GET)
    @ResponseBody
    public String hello() {
        System.out.print("helllllllo");
        return "hello";
    }

    // 测试
    @RequestMapping(value = "/test", method = RequestMethod.POST)
    @ResponseBody
    public String test(String insId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceNameLike(insId + "%").list().get(0);
        //System.out.println(processInstance.getId());
        Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        //System.out.println(task.getName() + "++++++++++++++");
        UserTask userTask = (UserTask) repositoryService.getBpmnModel(task.getProcessDefinitionId())
                .getFlowElement(task.getTaskDefinitionKey());

        List<FormProperty> formProperties = userTask.getFormProperties();
        //System.out.println(formProperties.get(0).getId());
        //System.out.println(formProperties.get(0).getName());// formproperties中的label
        //System.out.println(formProperties.get(0).getType());
        // System.out.println(formProperties.get(0).getFormValues().size());//defaultvalue在哪啊？？？

        List<DataAssociation> input = userTask.getDataInputAssociations();
        System.out.println(input.isEmpty());

        System.out.println(userTask.getFormKey());

        System.out.println(userTask.getExtensionElements().size());
        System.out.println(userTask.getExtensionElements().get("inputOutput").get(0).getChildElements().get(
                "inputParameter").get(0).getAttributes().get("name").get(0).getValue());// 取input的key
        System.out.println(userTask.getExtensionElements().get("inputOutput").get(0).getChildElements().get(
                "outputParameter").get(0).getElementText());// 取output的值
        System.out.println(userTask.getExtensionElements().containsKey("formProperty"));// formProperty是关键字，使用这种方法无法查询
        System.out.println(userTask.getExtensionElements().containsKey("properties"));
        System.out.println(userTask.getExtensionElements().containsKey("validation"));

        return "hello";
    }

    @RequestMapping(value = "/testwfDeploy", method = RequestMethod.POST)
    @ResponseBody
    public String testwfDeploy(String fileName, String fileContent) {
        try {
            List<Deployment> deploymentList = repositoryService.createDeploymentQuery().deploymentName(fileName).list();
            for (Deployment d : deploymentList) {
                repositoryService.deleteDeployment(d.getId(), true);// 默认是false true就是级联删除
            }

            Deployment deployment = repositoryService.createDeployment()// 创建Deployment对象
                    .addString(fileName, fileContent)
                    .name(fileName)
                    .deploy();
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("200");
            resultDTO.setMsg("success");
            return JSON.toJSONString(resultDTO);
        } catch (Exception e) {
            ActivitiUtils.clean(fileName);
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("500");
            resultDTO.setData(e.getMessage());
            resultDTO.setMsg("error");
            return JSON.toJSONString(resultDTO);
        }
    }

    @RequestMapping(value = "/testwfInstance", method = RequestMethod.POST)
    @ResponseBody
    public String testwfInstance(String fileName, String insId) {
        try {
            InsRet insRet = new InsRet();
            Deployment deployment = repositoryService.createDeploymentQuery().deploymentName(fileName).singleResult();
            String deploymentId = deployment.getId();
            List<ProcessDefinition> processDefinitionList = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deploymentId).list();
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionList.get(0).getId());
            Process mainProcess = bpmnModel.getMainProcess();
            /**
             * 定义流程变量
             */
            // VipChannel vipchannel = new VipChannel();
            // HashMap<String, Object> variables = new HashMap<>();
            // variables.put("variable", vipchannel);

            ProcessInstance mainProcessInstance = runtimeService.startProcessInstanceByKey(mainProcess.getId());
            runtimeService.setProcessInstanceName(mainProcessInstance.getId(), insId + ":");
            List<ProcessInstance> processInstanceList = runtimeService.createProcessInstanceQuery()
                    .processInstanceNameLike(insId + ":%").list();
            // List<Execution> executionList = runtimeService.createExecutionQuery().list();
            WfTask operationTask = new WfTask();
            operationTask.setName("instanceWorkflow");
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("200");
            List<WfTask> wfTaskList = new ArrayList<>();
            for (ProcessInstance processInstance : processInstanceList) {
                wfTaskList.addAll(ActivitiUtils.getWfTaskList(processInstance.getId()));
            }
            insRet.setTaskList(JSON.toJSONString(wfTaskList));
            insRet.setState("run");
            insRet.addOperation(JSON.toJSONString(operationTask));
            resultDTO.setData(JSON.toJSONString(insRet));
            resultDTO.setMsg("success");
            return JSON.toJSONString(resultDTO);
        } catch (Exception e) {
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("500");
            resultDTO.setData(e.getMessage());
            resultDTO.setMsg("error");
            return JSON.toJSONString(resultDTO);
        }
    }

    @RequestMapping(value = "/testwfComplete", method = RequestMethod.POST)
    @ResponseBody
    public String testwfComplete(String insId, String operation, String formVal, String priString, String cert,
            String serviceArgs) {
        //System.out.println(insId);
        //System.out.println(operation);
        //System.out.println(formVal);
        //System.out.println(priString);
        //System.out.println(cert);

        try {
            String timestamp = String.valueOf(new Date().getTime());
            InsRet insRet = new InsRet();
            // ProcessInstance mainProcessInstance =
            // runtimeService.createProcessInstanceQuery()
            // .processInstanceName(insId + ':').singleResult();
            // System.out.println(mainProcessInstance.getDeploymentId());
            List<ProcessInstance> processInstanceList = runtimeService.createProcessInstanceQuery()
                    .processInstanceNameLike(insId + "%").list();
            // System.out.println(processInstanceList.get(0).getProcessDefinitionId());
            // String deploymentId = mainProcessInstance.getDeploymentId();
            // 下面一句是将formval作为流程变量使用
            // List<FormProperty> formValList = JSON.parseArray(formVal,
            // FormProperty.class);

            WfTask operationTask = new WfTask();
            for (ProcessInstance processInstance : processInstanceList) {
                List<Task> taskList = taskService.createTaskQuery().processInstanceId(processInstance.getId())
                        .taskName(operation).list();
                for (Task t : taskList) {
                    Map<String, Object> variables = new HashMap<>();
                    if (runtimeService.getVariables(t.getExecutionId()).isEmpty()) {
                        UserTask userTask = (UserTask) repositoryService.getBpmnModel(t.getProcessDefinitionId())
                                .getFlowElement(t.getTaskDefinitionKey());
                        List<FormProperty> formValList = null;
                        if (!userTask.getFormProperties().isEmpty()) {
                            formValList = userTask.getFormProperties();
                        }
                        variables = ActivitiUtils.setVariables(formValList);
                    } else {
                        // if (formVal.equals("hhh")) {
                        // System.out.println("jinlaile");
                        // System.out.println(runtimeService.getVariable(t.getExecutionId(), "solve"));
                        // runtimeService.setVariable(t.getExecutionId(), "solve", "true");
                        // System.out.println(runtimeService.getVariable(t.getExecutionId(), "solve"));
                        // }
                        // 更改流程变量的位置
                        variables = runtimeService.getVariables(t.getExecutionId());
                    }
                    operationTask = ActivitiUtils.taskToWfTask(t);
                    if (operationTask.isRequestNode()) {
                        // String res = ActivitiUtils.getWebService(operationTask);//

                        // List<String> serviceArgList = JSON.parseArray(serviceArgs, String.class);
                        // ActivitiUtils.setSelectServiceParams(operationTask, serviceArgList.get(0));
                        // String selectServiceUrl = "http://127.0.0.1:8848/getUrl";
                        // Map<String, String> selectParamMap = getSelectParamMap(operationTask);
                        // // ResponseEntity<String> selectResponse =
                        // //
                        // requestserver(selectServiceUrl,"POST",JSONObject.toJSONString(selectParamMap));
                        // String url = "http://127.0.0.1:8848/getTranscript";
                        // String httpMethod = "POST";
                        // Map<String, String> requestParamMap = new HashMap<>();
                        // requestParamMap.put("studentName", "Ming");
                        // requestParamMap.put("studentId", "2620181111");
                        // requestParamMap.put("instanceId",
                        // "ins:summerCamp2020:RUC.12100000400002435L:1608604568674:People.346433200000000000:1608605408364");
                        // String requestParamStr = JSONObject.toJSONString(requestParamMap);
                        // rsaEncrypt.genKeyPair();
                        // String sign = rsaEncrypt.rsaSignToStr(requestParamStr.getBytes(),
                        // rsaEncrypt.getPrivateKey());
                        // requestParamMap.put("sign", sign);
                        // requestParamMap.put("cert", cert);
                        // // ResponseEntity<String> response =
                        // // requestserver(url,httpMethod,JSONObject.toJSONString(requestParamMap));
                        Map<String, String> messageStrMap = ActivitiUtils.getTaskMessageTarget(t);
                        for (String messageStr : messageStrMap.keySet()) {
                            if (messageStrMap.get(messageStr).equals("StartEvent")) {
                                ProcessInstance subProcessInstance = runtimeService
                                        .startProcessInstanceByKey(messageStr, variables);
                                runtimeService.setProcessInstanceName(subProcessInstance.getId(),
                                        insId + ':' + subProcessInstance.getProcessDefinitionKey());
                                WfOperation wfOperation = new WfOperation();
                                wfOperation.setType("StartEvent");
                                wfOperation.setKey(subProcessInstance.getProcessDefinitionKey());
                                insRet.addOperation(JSONObject.toJSONString(wfOperation));
                            } else if (messageStrMap.get(messageStr).equals("IntermediateCatchEvent")) {
                                for (ProcessInstance subProcessInstance : processInstanceList) {
                                    Execution execution = runtimeService.createExecutionQuery()
                                            .messageEventSubscriptionName(messageStr)
                                            .processInstanceId(subProcessInstance.getId())
                                            .singleResult();
                                    if (execution != null) {
                                        runtimeService.messageEventReceived(messageStr, execution.getId(),
                                                variables);
                                        WfOperation wfOperation = new WfOperation();
                                        wfOperation.setType("MessageIntermediateCatchEvent");
                                        wfOperation.setName(messageStr);
                                        insRet.addOperation(JSONObject.toJSONString(wfOperation));
                                    }
                                }
                            }
                        }
                        taskService.complete(t.getId(), variables);
                        insRet.addOperation(JSONObject.toJSONString(operationTask));
                    } else {
                        Map<String, String> messageStrMap = ActivitiUtils.getTaskMessageTarget(t);
                        for (String messageStr : messageStrMap.keySet()) {
                            if (messageStrMap.get(messageStr).equals("StartEvent")) {
                                ProcessInstance subProcessInstance = runtimeService
                                        .startProcessInstanceByKey(messageStr, variables);
                                runtimeService.setProcessInstanceName(subProcessInstance.getId(),
                                        insId + ':' + subProcessInstance.getProcessDefinitionKey());
                                WfOperation wfOperation = new WfOperation();
                                wfOperation.setType("StartEvent");
                                wfOperation.setKey(subProcessInstance.getProcessDefinitionKey());
                                insRet.addOperation(JSONObject.toJSONString(wfOperation));
                            } else if (messageStrMap.get(messageStr).equals("IntermediateCatchEvent")) {
                                for (ProcessInstance subProcessInstance : processInstanceList) {
                                    Execution execution = runtimeService.createExecutionQuery()
                                            .messageEventSubscriptionName(messageStr)
                                            .processInstanceId(subProcessInstance.getId())
                                            .singleResult();
                                    if (execution != null) {
                                        runtimeService.messageEventReceived(messageStr, execution.getId(),
                                                variables);
                                        WfOperation wfOperation = new WfOperation();
                                        wfOperation.setType("MessageIntermediateCatchEvent");
                                        wfOperation.setName(messageStr);
                                        insRet.addOperation(JSONObject.toJSONString(wfOperation));
                                    }
                                }
                            }
                        }
                        taskService.complete(t.getId(), variables);
                        insRet.addOperation(JSONObject.toJSONString(operationTask));
                    }
                }
            }
            List<WfTask> nextWfTaskList = new ArrayList<>();
            List<Task> nextTaskList = new ArrayList<>();
            processInstanceList = runtimeService.createProcessInstanceQuery().processInstanceNameLike(insId + ":%")
                    .list();
            for (ProcessInstance processInstance : processInstanceList) {
                nextWfTaskList.addAll(ActivitiUtils.getWfTaskList(processInstance.getId()));
            }
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("200");
            insRet.setTaskList(JSON.toJSONString(nextWfTaskList));
            insRet.setState(timestamp);
            // insRet.addOperation(JSON.toJSONString(operationTask));
            resultDTO.setData(JSON.toJSONString(insRet));
            resultDTO.setMsg("success");
            return JSON.toJSONString(resultDTO);
        } catch (Exception e) {
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("500");
            resultDTO.setData(e.getMessage());
            resultDTO.setMsg("error");
            return JSON.toJSONString(resultDTO);
        }
    }

    @RequestMapping(value = "/wfDeploy", method = RequestMethod.POST)
    @ResponseBody
    public String wfDeploy(String fileName, String fileContent) {
        try {
            // System.out.println(fileName);
            ActivitiUtils.clean(fileName);

            Deployment deployment = repositoryService.createDeployment()// 创建Deployment对象
                    .addString(fileName, fileContent)
                    .name(fileName)
                    .deploy();
            ActivitiUtils.clean(fileName);
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("200");
            resultDTO.setMsg("success");
            return JSON.toJSONString(resultDTO);
        } catch (Exception e) {
            ActivitiUtils.clean(fileName);
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("500");
            resultDTO.setData(e.getMessage());
            resultDTO.setMsg("error");
            return JSON.toJSONString(resultDTO);
        }

    }

    @RequestMapping(value = "/wfInstance", method = RequestMethod.POST)
    @ResponseBody
    public String wfInstance(String fileName, String fileContent) {
        try {
            InsRet insRet = new InsRet();
            ProcessInstance processInstance = ActivitiUtils.init(fileName, fileContent);
            if (processInstance == null) {
                ActivitiUtils.clean(fileName);
                WfResultDTO resultDTO = new WfResultDTO();
                resultDTO.setCode("500");
                resultDTO.setData("instance failed, there are no matching processDefinition and deployment.");
                resultDTO.setMsg("error");
                return JSON.toJSONString(resultDTO);
            }
            WfTask operationTask = new WfTask();
            operationTask.setName("instanceWorkflow");
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("200");
            insRet.setTaskList(JSON.toJSONString(ActivitiUtils.getWfTaskList(processInstance.getId())));
            insRet.setState("run");
            insRet.addOperation(JSON.toJSONString(operationTask));
            resultDTO.setData(JSON.toJSONString(insRet));
            resultDTO.setMsg("success");
            ActivitiUtils.clean(fileName);
            return JSON.toJSONString(resultDTO);
        } catch (Exception e) {
            ActivitiUtils.clean(fileName);
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("500");
            resultDTO.setData(e.getMessage());
            resultDTO.setMsg("error");
            return JSON.toJSONString(resultDTO);
        }

    }

    @RequestMapping(value = "/wfComplete", method = RequestMethod.POST)
    @ResponseBody
    public String wfComplete(String fileName, String fileContent, String history, String operation, String formVal) {
        try {
            InsRet insRet = new InsRet();
            ProcessInstance processInstance = ActivitiUtils.init(fileName, fileContent);
            if (processInstance == null) {
                ActivitiUtils.clean(fileName);
                WfResultDTO resultDTO = new WfResultDTO();
                resultDTO.setCode("500");
                resultDTO.setData("instance failed, there are no matching processDefinition and deployment.");
                resultDTO.setMsg("error");
                return JSON.toJSONString(resultDTO);
            }
            List<WfTask> wfTaskList = JSONObject.parseArray(history, WfTask.class);
            Map<String, Object> variables = null;
            TaskQuery taskQuery = null;
            for (WfTask wfTask : wfTaskList) {
                // 创建查询对象
                taskQuery = taskService.createTaskQuery();
                List<Task> taskList = taskQuery.list();
                for (Task t : taskList) {
                    variables = ActivitiUtils.setVariables(wfTask.nonJsonGetFormPropertiesList());
                    if (t.getName().equals(wfTask.getName())) {
                        taskService.complete(t.getId(), variables);
                    }
                }
            }
            variables = null;
            taskQuery = taskService.createTaskQuery();
            List<Task> taskList = taskQuery.list();
            WfTask operationTask = new WfTask();
            List<FormProperty> formValList = JSON.parseArray(formVal, FormProperty.class);
            for (Task t : taskList) {
                variables = ActivitiUtils.setVariables(formValList);
                if (t.getName().equals(operation)) {
                    operationTask = ActivitiUtils.taskToWfTask(t);
                    taskService.complete(t.getId(), variables);
                }
            }
            List<WfTask> nextWfTaskList = ActivitiUtils.getWfTaskList(processInstance.getId());
            ActivitiUtils.unifyAssignee(nextWfTaskList, wfTaskList);

            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("200");
            insRet.setTaskList(JSON.toJSONString(nextWfTaskList));
            insRet.setState("run");
            insRet.addOperation(JSON.toJSONString(operationTask));
            resultDTO.setData(JSON.toJSONString(insRet));
            resultDTO.setMsg("success");
            ActivitiUtils.clean(fileName);

            return JSON.toJSONString(resultDTO);
        } catch (Exception e) {
            ActivitiUtils.clean(fileName);
            WfResultDTO resultDTO = new WfResultDTO();
            resultDTO.setCode("500");
            resultDTO.setData(e.getMessage());
            resultDTO.setMsg("error");
            return JSON.toJSONString(resultDTO);
        }
    }

    @RequestMapping(value = "/requestserver", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> requestserver(String url, String httpMethod, String requestParams) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            JSONObject srequestParamsObject = JSONObject.parseObject(requestParams);
            for (String key : srequestParamsObject.keySet()) {
                params.set(key, srequestParamsObject.getString(key));
            }
            HttpHeaders headers = new HttpHeaders();
            HttpMethod method = HttpMethod.POST;
            headers.add("Content-Type", "multipart/form-data");
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, method, requestEntity, String.class);
            return response;

            /*
             * public byte[] rsaSign(String content, RSAPrivateKey priKey)
             * throws SignatureException {
             * try {
             * 
             * Signature signature = Signature.getInstance("SHA1withRSA");
             * signature.initSign(priKey);
             * signature.update(content.getBytes("utf-8"));
             * 
             * byte[] signed = signature.sign();
             * return signed;
             * } catch (Exception e) {
             * throw new SignatureException("RSAcontent = " + content
             * + "; charset = ", e);
             * }
             * }
             */
        } catch (Exception e) {
            ResponseEntity<String> response = new ResponseEntity<String>(e.getMessage(), HttpStatus.BAD_REQUEST);
            return response;
        }
    }

    public Map<String, String> getSelectParamMap(WfTask wfTask) {
        Map<String, String> selectParamMap = new HashMap<>();
        selectParamMap.put("serviceOrg", wfTask.getServiceOrg());
        selectParamMap.put("serviceName", wfTask.getServiceName());
        selectParamMap.put("serviceInterface", wfTask.getServiceInterface());
        selectParamMap.put("serviceRole", wfTask.getServiceRole());
        return selectParamMap;
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
