package com.wq.wfEngine.taskService;

import java.util.List;
import java.util.Map;

import com.wq.wfEngine.activiti.ActivitiUtils;
import com.wq.wfEngine.entity.WfOperation;


import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;

//跨组织启动intermediate message catch event
public class nextMessage implements JavaDelegate {
    private Expression nextMessage;

    public void execute(DelegateExecution execution) {
        RuntimeService runtimeService = ActivitiUtils.runtimeService;
        Map<String, Object> variables = runtimeService.getVariables(execution.getId());
        List<ProcessInstance> processInstanceList = runtimeService.createProcessInstanceQuery()
                .processInstanceNameLike(variables.get("insName").toString() + "%")
                .list();
        String messageStr = nextMessage.getValue(execution).toString();
        for (ProcessInstance subProcessInstance : processInstanceList) {
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .messageEventSubscriptionName(messageStr)
                    .processInstanceId(subProcessInstance.getId())
                    .list();
            for (Execution exe : executions) {
                if (exe != null) {
                    runtimeService.messageEventReceived(messageStr, exe.getId(),
                            variables);
                    WfOperation wfOperation = new WfOperation();
                    wfOperation.setType("MessageIntermediateCatchEvent");
                    wfOperation.setName(messageStr);
                }
            }
        }
    }
}