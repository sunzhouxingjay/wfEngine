package com.wq.wfEngine.taskService;

import java.util.Map;



import com.wq.wfEngine.activiti.ActivitiUtils;


import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.runtime.ProcessInstance;


public class nextProcess implements JavaDelegate {
    private Expression nextProcess;

    public void execute(DelegateExecution execution) {
        RuntimeService runtimeService=ActivitiUtils.runtimeService;
        Map<String,Object> variables=runtimeService.getVariables(execution.getId());
        if (nextProcess!=null) {
            ProcessInstance subProcessInstance = runtimeService
                .startProcessInstanceByKey(nextProcess.getValue(execution).toString(), variables);
            if (!subProcessInstance.isEnded()) {
                runtimeService.setProcessInstanceName(subProcessInstance.getId(),
                variables.get("insName").toString() + ':' + subProcessInstance.getProcessDefinitionKey());
            }
        }
    }
}
