package com.checkmarx.flow.cucumber.integration.cxconfigbugtracker;

import com.checkmarx.flow.CxFlowApplication;
import com.checkmarx.flow.config.FlowProperties;
import com.checkmarx.flow.config.GitHubProperties;
import com.checkmarx.flow.config.JiraProperties;
import com.checkmarx.flow.controller.GitHubController;
import com.checkmarx.flow.dto.BugTracker;
import com.checkmarx.flow.dto.ScanRequest;
import com.checkmarx.flow.dto.github.*;
import com.checkmarx.flow.service.FlowService;
import com.checkmarx.flow.service.GitHubService;
import com.checkmarx.flow.service.HelperService;
import com.checkmarx.jira.PublishUtils;
import com.checkmarx.sdk.config.Constants;
import com.checkmarx.sdk.config.CxProperties;
import com.checkmarx.sdk.dto.ScanResults;
import com.checkmarx.sdk.dto.cx.CxScanSummary;
import com.checkmarx.sdk.exception.CheckmarxException;
import com.checkmarx.sdk.service.CxClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {CxFlowApplication.class, CxConfigBugTrackerConfiguration.class, PublishUtils.class})
@Slf4j
public class CxConfigBugTrackerSteps {
    private static final String PULL_REQUEST_STATUSES_URL = "statuses url stub";
    public static final String BRANCH_NAME = "udi-tests";
    public static final String CUSTOM_BEAN_NAME = "GitHub";

    @Autowired
    private CxClient cxClientMock;
    private final GitHubService gitHubService;
    private GitHubController gitHubControllerSpy;
    private final ObjectMapper mapper = new ObjectMapper();
    private final FlowProperties flowProperties;
    private final CxProperties cxProperties;
    private final GitHubProperties gitHubProperties;
    private final HelperService helperService;
    private ScanResults scanResultsToInject;

    private String branch;
    private ScanRequest request;
    private final JiraProperties jiraProperties;


    public CxConfigBugTrackerSteps(FlowProperties flowProperties, GitHubService gitHubService,
                                   CxProperties cxProperties, GitHubProperties gitHubProperties, JiraProperties jiraProperties, GitHubController gitHubController) {


        this.flowProperties = flowProperties;

        this.cxProperties = cxProperties;
        this.jiraProperties = jiraProperties;
        this.helperService = mock(HelperService.class);
        this.gitHubService = gitHubService;

        this.gitHubProperties = gitHubProperties;
        this.gitHubControllerSpy = gitHubController;
        initGitHubProperties();
    }

    private void initGitHubProperties() {
        this.gitHubProperties.setCxSummary(false);
        this.gitHubProperties.setFlowSummary(false);
        this.gitHubProperties.setUrl("https://github.com/cxflowtestuser/CxConfigTests");
        this.gitHubProperties.setWebhookToken("1234");
        this.gitHubProperties.setConfigAsCode("cx.config.json");
        this.gitHubProperties.setApiUrl("https://api.github.com/repos");

    }

    @Before
    public void prepareServices() {
        initCxClientMock();
        scanResultsToInject = createFakeScanResults();
        initHelperServiceMock();
        cleanRequest();
        fixBranch();
    }

    private void initGitHubControllerSpy() {
        doNothing().when(gitHubControllerSpy).verifyHmacSignature(any(), any());
    }

    private void fixBranch() {
        if (!flowProperties.getBranches().contains(BRANCH_NAME)) {
            flowProperties.getBranches().add(BRANCH_NAME);
        }
    }

    private void cleanRequest() {
        this.request = null;
    }

    private void initHelperServiceMock() {
        when(helperService.isBranch2Scan(any(), anyList())).thenReturn(true);
        when(helperService.getShortUid()).thenReturn("123456");
    }

    private void initCxClientMock() {
        try {
            CxConfigBugTrackerSteps.ScanResultsAnswerer answerer = new CxConfigBugTrackerSteps.ScanResultsAnswerer();
            when(cxClientMock.getReportContentByScanId(anyInt(), any())).thenAnswer(answerer);
            when(cxClientMock.getTeamId(anyString())).thenReturn("teamId");
        } catch (CheckmarxException e) {
            Assert.fail("Error initializing mock." + e);
        }
    }

    private static ScanResults createFakeScanResults() {
        ScanResults result = new ScanResults();
        result.setScanSummary(new CxScanSummary());
        Map<String, Object> details = new HashMap<>();
        details.put(Constants.SUMMARY_KEY, new HashMap<>());
        result.setAdditionalDetails(details);
        result.setXIssues(new ArrayList<>());
        return result;
    }


    @Given("github branch is udi-tests")
    public void setBranchAndCreatePullReqeust(){
        this.branch = BRANCH_NAME;
    }

    @When("pull request webhook arrives")
    public void sendPullRequestWebhookEvent() throws InterruptedException {
        assertFlowPropertiesBugTracker("NONE");
        ArgumentCaptor<ScanRequest> ac = ArgumentCaptor.forClass(ScanRequest.class);
        FlowService flowServiceMock = Mockito.mock(FlowService.class);
        gitHubControllerSpy = new GitHubController(gitHubProperties,flowProperties, cxProperties, jiraProperties, flowServiceMock,helperService, gitHubService, null);
        gitHubControllerSpy = spy(gitHubControllerSpy);
        initGitHubControllerSpy();
        buildPullRequest();
        verify(flowServiceMock, times(1)).initiateAutomation(ac.capture());
        request = ac.getValue();
    }

    private void assertFlowPropertiesBugTracker(String expected) {
        Assert.assertEquals(expected, flowProperties.getBugTracker());
    }

    @Then("scan request should have CUSTOM bug tracker, and GitHub custom bean name")
    public void assertIssuesInGitHb() {
        Assert.assertEquals(BugTracker.Type.CUSTOM, request.getBugTracker().getType());
        Assert.assertEquals(CUSTOM_BEAN_NAME, request.getBugTracker().getCustomBean());
    }

    public void buildPullRequest() {
        PullEvent pullEvent = new PullEvent();
        Repository repo = new Repository();
        repo.setName("CxConfigTests");

        repo.setCloneUrl(gitHubProperties.getUrl());
        Owner owner = new Owner();
        owner.setName("");
        owner.setLogin("cxflowtestuser");
        repo.setOwner(owner);
        pullEvent.setRepository(repo);
        pullEvent.setAction("opened");
        PullRequest pullRequest = new PullRequest();
        pullRequest.setIssueUrl("");
        Head headBranch = new Head();
        headBranch.setRef(branch);

        pullRequest.setHead(headBranch);
        pullRequest.setBase(new Base());
        pullRequest.setStatusesUrl("");

        pullEvent.setPullRequest(pullRequest);

        try {
            String pullEventStr = mapper.writeValueAsString(pullEvent);

            gitHubControllerSpy.pullRequest(
                    pullEventStr,
                    "SIGNATURE",
                    "CX", "VB",
                    Arrays.asList(branch), null,
                    null,
                    null,
                    "VB",
                    "\\CxServer\\SP",
                    null,
                    "",
                    "default",
                    false,
                    null,
                    null,
                    null,
                    null,
                    null);

        } catch (JsonProcessingException e) {
            fail("Unable to parse " + pullEvent.toString());
        }
    }

     /**
     * Returns scan results as if they were produced by SAST.
     */
    private class ScanResultsAnswerer implements Answer<ScanResults> {
        @Override
        public ScanResults answer(InvocationOnMock invocation) {
            return scanResultsToInject;
        }
    }
}
