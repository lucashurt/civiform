package services.email.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import com.microsoft.graph.users.item.sendmail.SendMailRequestBuilder;
import com.microsoft.kiota.ApiException;
import com.typesafe.config.Config;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.Environment;
import repository.ResetPostgres;
import services.cloud.azure.Credentials;
import services.monitoring.EmailSendMetrics;

public class GraphApiEmailClientTest extends ResetPostgres {
  private final Config mockConfig = mock(Config.class);
  private final Environment mockEnvironment = mock(Environment.class);
  private GraphApiEmailClient emailClient;
  private GraphServiceClient graphClient;

  @Before
  public void setup() {
    when(mockConfig.getString(GraphApiEmailClient.AZURE_SENDER_CONF_PATH))
        .thenReturn("test@example.com");
    when(mockEnvironment.isProd()).thenReturn(false);

    emailClient =
        new GraphApiEmailClient(
            instanceOf(Credentials.class),
            mockConfig,
            mockEnvironment,
            instanceOf(EmailSendMetrics.class));

    graphClient = emailClient.getClient().get();

    when(graphClient.me()).thenReturn(mock(UserItemRequestBuilder.class));
    when(graphClient.me().sendMail()).thenReturn(mock(SendMailRequestBuilder.class));
  }

  @Test
  public void send_success_nonProd() throws ApiException {
    String toAddress = "recipient@example.com";
    String subject = "Test Subject";
    String body = "Test Body";

    emailClient.send(toAddress, subject, body);

    ArgumentCaptor<SendMailPostRequestBody> requestCaptor =
        ArgumentCaptor.forClass(SendMailPostRequestBody.class);
    verify(graphClient.me().sendMail()).post(requestCaptor.capture());
    SendMailPostRequestBody requestBody = requestCaptor.getValue();

    assertEquals("[Test Message] Test Subject", requestBody.getMessage().getSubject());
    assertTrue(
        requestBody
            .getMessage()
            .getBody()
            .getContent()
            .contains("This email was generated from our test server."));
    assertEquals(
        "test@example.com", requestBody.getMessage().getFrom().getEmailAddress().getAddress());
    assertEquals(
        toAddress,
        requestBody.getMessage().getToRecipients().get(0).getEmailAddress().getAddress());
  }

  @Test
  public void send_success_prod() throws ApiException {
    when(mockEnvironment.isProd()).thenReturn(true);

    String toAddress = "recipient@example.com";
    String subject = "Prod Subject";
    String body = "Prod Body";

    emailClient.send(toAddress, subject, body);

    ArgumentCaptor<SendMailPostRequestBody> requestCaptor =
        ArgumentCaptor.forClass(SendMailPostRequestBody.class);
    verify(graphClient.me().sendMail()).post(requestCaptor.capture());
    SendMailPostRequestBody requestBody = requestCaptor.getValue();

    assertEquals("Prod Subject", requestBody.getMessage().getSubject());
    assertFalse(
        requestBody
            .getMessage()
            .getBody()
            .getContent()
            .contains("This email was generated from our test server."));
    assertTrue(requestBody.getMessage().getBody().getContent().contains("Prod Body"));
    assertEquals(
        "test@example.com", requestBody.getMessage().getFrom().getEmailAddress().getAddress());
    assertEquals(
        toAddress,
        requestBody.getMessage().getToRecipients().get(0).getEmailAddress().getAddress());
  }

  @Test
  public void send_failure_prod() throws ApiException {
    when(mockEnvironment.isProd()).thenReturn(true);

    String toAddress = "recipient@example.com";
    String subject = "Test Subject";
    String body = "Test Body";

    emailClient.send(toAddress, subject, body);

    // Simulate an API exception
    when(graphClient.me().sendMail()).thenThrow(new ApiException());

    emailClient.send(toAddress, subject, body);

    assertThrows(ApiException.class, () -> graphClient.me().sendMail());
  }

  @Test
  public void send_failure_nonProd() throws ApiException {
    when(mockEnvironment.isProd()).thenReturn(false);

    String toAddress = "recipient@example.com";
    String subject = "Test Subject";
    String body = "Test Body";

    emailClient.send(toAddress, subject, body);

    // Simulate an API exception
    when(graphClient.me().sendMail()).thenThrow(new ApiException());

    emailClient.send(toAddress, subject, body);

    assertThrows(ApiException.class, () -> graphClient.me().sendMail());
  }

  @Test
  public void testSend_emptyRecipients() {
    emailClient.send(ImmutableList.of(), "subject", "body");

    verify(graphClient.me().sendMail(), never()).post(any());
  }
}