@Integration
Feature: as a product owner, I want to create a generic layer to support non-functional requirements of issue handling.
  This feature refers to the following issue trackers: Azure DevOps, GitHub and Jira.

  Scenario Outline: Analytics data should be reported in the same manner by issue trackers
    Given issue tracker is set to "<issue tracker>"
    When getting SAST results
    Then analytics report of type GetResultsReport is logged
    # also verify some fields in the analytics report

    Examples:
      | issue tracker |
      | GitHub        |
      | Azure         |
      | JIRA          |


  Scenario Outline: Errors should be handled in the same manner when an issue tracker is not available
    Given issue tracker is set to "<issue tracker>"
    And issue tracker is not available
    When publishing scan results
    Then an exception of type "?" is thrown with the message: "?"

    Examples:
      | issue tracker |
      | GitHub        |
      | Azure         |
      | JIRA          |


  Scenario Outline: Errors should be handled in the same manner when an issue tracker fails to create an issue
    Given issue tracker is set to "<issue tracker>"
    When publishing scan results
    And issue tracker service fails to create an issue
    Then an exception of type "?" is thrown with the message: "?"

    Examples:
      | issue tracker |
      | GitHub        |
      | Azure         |
      | JIRA          |
