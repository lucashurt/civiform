package services.applicant.question;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Locale;
import java.util.Optional;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import models.Applicant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import play.i18n.Lang;
import play.i18n.Messages;
import play.i18n.MessagesApi;
import repository.WithPostgresContainer;
import services.Path;
import services.applicant.ApplicantData;
import services.question.types.TextQuestionDefinition;
import support.QuestionAnswerer;

@RunWith(JUnitParamsRunner.class)
public class TextQuestionTest extends WithPostgresContainer {
  private static final TextQuestionDefinition textQuestionDefinition =
      new TextQuestionDefinition(
          "question name",
          Path.create("applicant.my.path.name"),
          Optional.empty(),
          "description",
          ImmutableMap.of(Locale.US, "question?"),
          ImmutableMap.of(Locale.US, "help text"));

  private static final TextQuestionDefinition minAndMaxLengthTextQuestionDefinition =
      new TextQuestionDefinition(
          "question name",
          Path.create("applicant.my.path.name"),
          Optional.empty(),
          "description",
          ImmutableMap.of(Locale.US, "question?"),
          ImmutableMap.of(Locale.US, "help text"),
          TextQuestionDefinition.TextValidationPredicates.create(3, 4));

  private Applicant applicant;
  private ApplicantData applicantData;
  private Messages messages;

  @Before
  public void setUp() {
    applicant = new Applicant();
    applicantData = applicant.getApplicantData();
    messages = instanceOf(MessagesApi.class).preferred(ImmutableList.of(Lang.defaultLang()));
  }

  @Test
  public void withEmptyApplicantData() {
    ApplicantQuestion applicantQuestion =
        new ApplicantQuestion(textQuestionDefinition, applicantData, ApplicantData.APPLICANT_PATH);

    TextQuestion textQuestion = new TextQuestion(applicantQuestion);

    assertThat(textQuestion.hasTypeSpecificErrors()).isFalse();
    assertThat(textQuestion.hasQuestionErrors()).isFalse();
  }

  @Test
  public void withApplicantData_passesValidation() {
    ApplicantQuestion applicantQuestion =
        new ApplicantQuestion(textQuestionDefinition, applicantData, ApplicantData.APPLICANT_PATH);
    QuestionAnswerer.answerTextQuestion(
        applicantData, applicantQuestion.getContextualizedPath(), "hello");

    TextQuestion textQuestion = new TextQuestion(applicantQuestion);

    assertThat(textQuestion.getTextValue().get()).isEqualTo("hello");
    assertThat(textQuestion.hasTypeSpecificErrors()).isFalse();
    assertThat(textQuestion.hasQuestionErrors()).isFalse();
  }

  @Test
  @Parameters({"abc", "abcd"})
  public void withMinAndMaxLength_withValidApplicantData_passesValidation(String value) {
    ApplicantQuestion applicantQuestion =
        new ApplicantQuestion(
            minAndMaxLengthTextQuestionDefinition, applicantData, ApplicantData.APPLICANT_PATH);
    QuestionAnswerer.answerTextQuestion(
        applicantData, applicantQuestion.getContextualizedPath(), value);

    TextQuestion textQuestion = new TextQuestion(applicantQuestion);

    assertThat(textQuestion.getTextValue().get()).isEqualTo(value);
    assertThat(textQuestion.hasTypeSpecificErrors()).isFalse();
    assertThat(textQuestion.hasQuestionErrors()).isFalse();
  }

  @Test
  @Parameters({
    ",Must contain at least 3 characters.",
    "a,Must contain at least 3 characters.",
    "abcde,Must contain at most 4 characters."
  })
  public void withMinAndMaxLength_withInvalidApplicantData_failsValidation(
      String value, String expectedErrorMessage) {
    ApplicantQuestion applicantQuestion =
        new ApplicantQuestion(
            minAndMaxLengthTextQuestionDefinition, applicantData, ApplicantData.APPLICANT_PATH);
    QuestionAnswerer.answerTextQuestion(
        applicantData, applicantQuestion.getContextualizedPath(), value);

    TextQuestion textQuestion = new TextQuestion(applicantQuestion);

    if (textQuestion.getTextValue().isPresent()) {
      assertThat(textQuestion.getTextValue().get()).isEqualTo(value);
    }
    assertThat(textQuestion.hasTypeSpecificErrors()).isFalse();
    assertThat(textQuestion.getQuestionErrors()).hasSize(1);
    String errorMessage = textQuestion.getQuestionErrors().iterator().next().getMessage(messages);
    assertThat(errorMessage).isEqualTo(expectedErrorMessage);
  }
}
