package io.choerodon.workflow.app.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import io.choerodon.asgard.saga.annotation.Saga;
import io.choerodon.asgard.saga.feign.SagaClient;
import io.choerodon.asgard.saga.producer.StartSagaBuilder;
import io.choerodon.asgard.saga.producer.TransactionalProducer;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.workflow.api.controller.dto.DevopsPipelineDTO;
import io.choerodon.workflow.app.service.ProcessInstanceService;
import io.choerodon.workflow.domain.handler.DevopsPipelineBpmnHandler;
import io.choerodon.workflow.domain.repository.DevopsServiceRepository;
import io.choerodon.workflow.infra.util.ActivitiUserLoginUtil;
import io.choerodon.workflow.infra.util.DynamicWorkflowUtil;
import org.activiti.api.process.model.ProcessInstance;
import org.activiti.api.process.model.builders.ProcessPayloadBuilder;
import org.activiti.api.process.model.payloads.DeleteProcessPayload;
import org.activiti.api.process.model.payloads.GetProcessInstancesPayload;
import org.activiti.api.process.runtime.ProcessRuntime;
import org.activiti.api.runtime.shared.query.Page;
import org.activiti.api.runtime.shared.query.Pageable;
import org.activiti.api.task.model.Task;
import org.activiti.api.task.model.builders.TaskPayloadBuilder;
import org.activiti.api.task.model.payloads.GetTasksPayload;
import org.activiti.api.task.runtime.TaskRuntime;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Created by Sheep on 2019/4/2.
 */
@Service
public class ProcessInstanceServiceImpl implements ProcessInstanceService {


    private Gson gson = new Gson();

    @Autowired
    DevopsServiceRepository devopsServiceRepository;

    @Autowired
    ActivitiUserLoginUtil activitiUserLoginUtil;
    @Autowired
    RepositoryService repositoryService;
    @Autowired
    ProcessRuntime processRuntime;
    @Autowired
    TaskRuntime taskRuntime;
    @Autowired
    SagaClient sagaClient;
    @Autowired
    private TransactionalProducer producer;


    private Logger logger = LoggerFactory.getLogger(ProcessInstanceServiceImpl.class);


    @Override
    public void beginDevopsPipeline(DevopsPipelineDTO devopsPipelineDTO) {

        Map<String, Object> params = new HashMap<>();
        BpmnModel model = DevopsPipelineBpmnHandler.initDevopsCDPipelineBpmn(devopsPipelineDTO, params);

        if (!DynamicWorkflowUtil.checkValidate(model)) {
            throw new CommonException("invlid workflow module");
        }
        String filePath = "bmpn/" + UUID.randomUUID().toString() + ".bpmn";
        Deployment deployment = repositoryService.createDeployment().addBpmnModel(filePath, model).name("test").deploy();

        org.activiti.engine.repository.ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId()).singleResult();
        String name = "部署CD流程";

        logger.info(String.format("部署CD流程:%s  开始", devopsPipelineDTO.getPipelineRecordId()));
        processRuntime.start(ProcessPayloadBuilder
                .start()
                .withProcessDefinitionKey(processDefinition.getKey())
                .withName(name)
                .withBusinessKey(devopsPipelineDTO.getBusinessKey())
                .withVariables(params)
                .build());
    }

    @Override
    public Boolean approveUserTask(String businessKey) {
        GetProcessInstancesPayload getProcessInstancesPayload = new GetProcessInstancesPayload();
        getProcessInstancesPayload.setBusinessKey(businessKey);
        Page<ProcessInstance> processInstances = processRuntime.processInstances(Pageable.of(0, 10), getProcessInstancesPayload);
        if (processInstances.getContent().size() > 0) {
            GetTasksPayload getTasksPayload = new GetTasksPayload();
            getTasksPayload.setProcessInstanceId(processInstances.getContent().get(0).getId());
            Page<Task> tasks = taskRuntime.tasks(Pageable.of(0, 10), getTasksPayload);
            taskRuntime.complete(TaskPayloadBuilder.complete().withTaskId(tasks.getContent().get(0).getId()).build());
            return true;
        }
        return false;
    }


    @Override
    public void stopInstance(String businessKey) {
        GetProcessInstancesPayload getProcessInstancesPayload = new GetProcessInstancesPayload();
        getProcessInstancesPayload.setBusinessKey(businessKey);
        Page<ProcessInstance> processInstances = processRuntime.processInstances(Pageable.of(0, 10), getProcessInstancesPayload);
        if (processInstances.getContent().size() > 0) {
            DeleteProcessPayload deleteProcessPayload = new DeleteProcessPayload();
            deleteProcessPayload.setProcessInstanceId(processInstances.getContent().get(0).getId());
            processRuntime.delete(deleteProcessPayload);
        }
    }

}
