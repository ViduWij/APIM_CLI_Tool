package utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Scanner;

/**
 * Created by Vidu Wijayaweera on 8/28/2017.
 */
public class APISubscription {

    private static final Log log = LogFactory.getLog(APIExporter.class);
    private static JSONParser parser = new JSONParser();
    private static ObjectMapper mapper = new ObjectMapper();

    static String getAPIDetails() throws APISubscriptionException {

        Scanner scanner = new Scanner(System.in, ImportExportConstants.CHARSET);
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

        if (StringUtils.isBlank(config.getApiName())) {
            System.out.print("Enter the name of the API : ");
            String name = scanner.next();
            if (StringUtils.isNotBlank(name)) {
                config.setApiName(name);
            }
        }
        if (StringUtils.isBlank(config.getApiProvider())) {
            System.out.print("Enter the provider of the API: ");
            String provider = scanner.next();
            if (StringUtils.isNotBlank(provider)) {
                config.setApiProvider(provider);
            }
        }
        if (StringUtils.isBlank(config.getApiVersion())) {
            System.out.print("Enter the version of the API: ");
            String version = scanner.next();
            if (StringUtils.isNotBlank(version)) {
                config.setApiVersion(version);
            }
        }

        //Building the API id
        String apiId = config.getApiProvider() + "-" + config.getApiName() + "-" + config.getApiVersion();
        return apiId;

    }

    static String createAccessToken(String consumerCredentials) throws APISubscriptionException {
        //Generating access token
        String token;
        try {
            token = ImportExportUtils.getAccessToken(ImportExportConstants.SUBSCRIPTION_SCOPE, consumerCredentials);
            System.out.println("Access Token for Subscription: " + token + "\n");
        } catch (UtilException e) {
            String errorMsg = "Error occurred while generating access token";
            log.error(errorMsg, e);
            throw new APISubscriptionException(errorMsg, e);
        }

        return token;
    }

    static void addNewSubscription(String consumerCredentials) throws IOException, APISubscriptionException {

        try {
            ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
            String accessToken = createAccessToken(consumerCredentials);

            Scanner scanner = new Scanner(System.in, ImportExportConstants.CHARSET);
            String apiId = getAPIDetails();

            getAPITiers(accessToken, config.getApiName(), apiId);

            if (StringUtils.isBlank(config.getAPITier())) {
                System.out.print("\nEnter the letter of the Tier to select: ");
                String tier = scanner.next();

                if (tier.equals("U") || tier.equals("u")) {
                    if (StringUtils.isNotBlank(tier)) {
                        config.setAPITier("Unlimited");
                    }
                } else if (tier.equals("G") || tier.equals("g")) {
                    if (StringUtils.isNotBlank(tier)) {
                        config.setAPITier("Gold");
                    }
                } else if (tier.equals("S") || tier.equals("s")) {
                    if (StringUtils.isNotBlank(tier)) {
                        config.setAPITier("Silver");
                    }
                }
            }

            showExistingApplications(accessToken);
            System.out.println("\nDo you want to create a new application? Y/N ");
            String decision = scanner.next();

            if (decision.equals("Y") || decision.equals("y")) {
                String applicationName = createNewApplication(accessToken);
                config.setApplicationName(applicationName);
            } else if (decision.equals("N") || decision.equals("n")) {
                if (StringUtils.isBlank(config.getApplicationName())) {
                    System.out.print("Enter the name of the application to add the subscription: ");
                    String appName = scanner.next();
                    if (StringUtils.isNotBlank(appName)) {
                        config.setApplicationName(appName);
                    }
                }
            } else {
                System.out.print("Enter valid letter. Y/N ");
                decision = scanner.next();
            }

            getApplicatonID(config.getApplicationName(), accessToken);
            String responseString;
            CloseableHttpResponse response;
            CloseableHttpClient client = null;

            try {
                client = HttpClientGenerator.getHttpClient();
            } catch (UtilException e) {
                String errorMsg = "Error occurred while getting a closableHttpClient for creating" +
                        " the API";
                log.error(errorMsg, e);
                return;
            }

            String url = config.getStoreUrl() + "/subscriptions";
            HttpPost request = new HttpPost(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + accessToken);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);

            String tier = config.getAPITier();
            String applicationID = config.getApplicationID();

            String jsonBody = "{  \"tier\": \"" + tier + "\",\n" +
                    "    \"apiIdentifier\": \"" + apiId + "\",\n" +
                    "    \"applicationId\": \"" + applicationID + "\" }";

            request.setEntity(new StringEntity(jsonBody));
            response = client.execute(request);

            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {

                try {

                    System.out.println("Subscription Status OK..! \n");

                } catch (Exception e) {
                    String errorMsg = "Error in subscribing the API " + apiId;
                    log.error(errorMsg, e);
                    throw new APISubscriptionException(errorMsg, e);
                }

            } else if (response.getStatusLine().getStatusCode() == Response.Status.CONFLICT.getStatusCode()) {
                log.error(" Cannot subscribe, Subscription to API " + config.getApiName() + " already exists with application, " + config.getApplicationName());
            } else if (response.getStatusLine().getStatusCode() == Response.Status.CREATED.getStatusCode()) {

                try {

                    System.out.println("\nSubscription for API " + config.getApiName() + " added successfully in " + config.getApplicationName() + "..!\n");
                    HttpEntity entity = response.getEntity();
                    responseString = EntityUtils.toString(entity);
                    JSONObject content = (JSONObject) parser.parse(responseString);

                    System.out.println("Subscription Id: " + (String) content.get("subscriptionId"));
                    System.out.println("Application Id: " + (String) content.get("applicationId"));
                    System.out.println("API Identifier: " + (String) content.get("apiIdentifier"));
                    System.out.println("Subscribed Tier: " + (String) content.get("tier"));
                    System.out.println("Status: " + (String) content.get("status"));


                } catch (Exception e) {
                    String errorMsg = "Error in subscribing the API " + apiId;
                    log.error(errorMsg, e);
                    throw new APISubscriptionException(errorMsg, e);
                }

            } else if (response.getStatusLine().getStatusCode() == Response.Status.FORBIDDEN.getStatusCode()) {
                log.error("cannot subscribe different APIs with duplicate context exists");
            } else if (response.getStatusLine().getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
                log.error(EntityUtils.toString(response.getEntity()));
            } else if (response.getStatusLine().getStatusCode() == Response.Status.UNAUTHORIZED.getStatusCode()) {
                log.error("Unauthorized request, You cannot subscribe the API since scope validation " +
                        "failed");
            } else {
                log.error("Error occurred while subscribing the API ");
            }
        } catch (IOException e) {
            log.error("Error occurred while subscribing the API ", e);
        }
    }

    private static void getAPITiers(String accessToken, String apiName, String apiID) {

        try {
            ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
            String responseString;
            CloseableHttpResponse response;
            CloseableHttpClient client = null;

            client = HttpClientGenerator.getHttpClient();
            String url = config.getStoreUrl() + "/apis/" + apiID;
            HttpGet request = new HttpGet(url);

            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                    accessToken);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            response = client.execute(request);

            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                try {
                    System.out.println("\nAvailable Tiers for the API " + apiName + ".... ");

                    HttpEntity entity = response.getEntity();
                    responseString = EntityUtils.toString(entity);
                    JSONObject json = (JSONObject) parser.parse(responseString);
                    JSONArray tiers = (JSONArray) json.get("tiers");

                    for (int i = 0; i < tiers.size(); i++) {
                        //        System.out.println(tiers.get(i));

                        if (tiers.get(i).equals("Unlimited")) {
                            System.out.println("U => Unlimited Requests");
                        } else if (tiers.get(i).equals("Gold")) {
                            System.out.println("G => Gold: 5000 Requests per minute");
                        } else if (tiers.get(i).equals("Silver")) {
                            System.out.println("S => Silver: 2000 Requests per minute");
                        }
                    }

                } catch (IOException e) {
                    String errorMsg = "Error occurred while converting API response to string";
                    log.error(errorMsg, e);
                    throw new APISubscriptionException(errorMsg, e);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.NOT_FOUND.getStatusCode()) {
                String message = "Tiers does not exist/ not found for the API " + apiName;
                log.warn(message);
                throw new APISubscriptionException(message);
            } else {
                String errorMsg = "Error occurred while retrieving the information of API " + apiName;
                log.error(errorMsg);
                throw new APISubscriptionException(errorMsg);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    static void listSubscriptionsByAPI(String consumerCredentials) throws APISubscriptionException {

        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

        String apiId = getAPIDetails();
        String accessToken = createAccessToken(consumerCredentials);

        String responseString;
        CloseableHttpResponse response;
        CloseableHttpClient client = null;

        try {
            //Retrieve API subscription- information
            client = HttpClientGenerator.getHttpClient();
            String url = config.getStoreUrl() + "/subscriptions?apiId=" + apiId;
            HttpGet request = new HttpGet(url);

            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                    accessToken);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            response = client.execute(request);

        } catch (IOException e) {
            String errorMsg = "Error occurred while retrieving details of the API " + apiId;
            log.error(errorMsg, e);
            throw new APISubscriptionException(errorMsg, e);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closable http client for execution";
            log.error(errorMsg, e);
            throw new APISubscriptionException(errorMsg, e);
        }
        if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
            try {
                HttpEntity entity = response.getEntity();
                responseString = EntityUtils.toString(entity);

                //System.out.println("response String... " + responseString);
                JSONObject json = (JSONObject) parser.parse(responseString);
                System.out.println("Number of Subscriptions for API " + config.getApiName() + " : " + json.get("count"));
                JSONArray list = (JSONArray) json.get("list");

                for (int i = 0; i < list.size(); i++) {
                    JSONObject content = (JSONObject) list.get(i);
                    String subscriptionId = (String) content.get("subscriptionId");
                    String applicationId = (String) content.get("applicationId");
                    String apiIdentifier = (String) content.get("apiIdentifier");
                    String tier = (String) content.get("tier");
                    String status = (String) content.get("status");

                    System.out.println("\nSubscription Id: " + subscriptionId);
                    System.out.println("Application Id: " + applicationId);
                    System.out.println("ApiIdentifier Id: " + apiIdentifier);
                    System.out.println("Tier: " + tier);
                    System.out.println("Status: " + status);
                }

            } catch (IOException e) {
                String errorMsg = "Error occurred while converting API response to string";
                log.error(errorMsg, e);
                throw new APISubscriptionException(errorMsg, e);
            } catch (ParseException e) {
                e.printStackTrace();
            }

        } else if (response.getStatusLine().getStatusCode() ==
                Response.Status.NOT_FOUND.getStatusCode()) {
            String message = "API " + apiId + " does not exist/ not found ";
            log.warn(message);
            throw new APISubscriptionException(message);
        } else {
            String errorMsg = "Error occurred while retrieving the subscription-information of API " + apiId;
            log.error(errorMsg);
            throw new APISubscriptionException(errorMsg);
        }
    }

    private static void getApplicatonID(String applicationName, String accessToken) {

        try {
            ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
            String responseString;
            CloseableHttpResponse response;
            CloseableHttpClient client = null;

            client = HttpClientGenerator.getHttpClient();
            String url = config.getStoreUrl() + "/applications";
            HttpGet request = new HttpGet(url);

            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                    accessToken);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            response = client.execute(request);


            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                try {
                    HttpEntity entity = response.getEntity();
                    responseString = EntityUtils.toString(entity);
                    JSONObject json = (JSONObject) parser.parse(responseString);
                    JSONArray list = (JSONArray) json.get("list");


                    for (int i = 0; i < list.size(); i++) {
                        JSONObject content = (JSONObject) list.get(i);

                        String name = (String) content.get("name");
                        String applicationId = (String) content.get("applicationId");

                        if (name.equals(applicationName)) {
                            config.setApplicationID(applicationId);
                            break;
                        } else {
                            config.setApplicationID("Not Found");
                        }
                    }

                } catch (IOException e) {
                    String errorMsg = "Error occurred while converting API response to string";
                    log.error(errorMsg, e);
                    throw new APISubscriptionException(errorMsg, e);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.NOT_FOUND.getStatusCode()) {
                String message = "Application " + applicationName + " does not exist/ not found ";
                log.warn(message);
                throw new APISubscriptionException(message);
            } else {
                String errorMsg = "Error occurred while retrieving the information of Application " + applicationName;
                log.error(errorMsg);
                throw new APISubscriptionException(errorMsg);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    static String createNewApplication(String accessToken) throws APISubscriptionException {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        try {
            Scanner scanner = new Scanner(System.in, ImportExportConstants.CHARSET);
            String apiId = getAPIDetails();

            if (StringUtils.isBlank(config.getApplicationName())) {
                System.out.print("Enter a name for the Application: ");
                String name = scanner.next();
                if (StringUtils.isNotBlank(name)) {
                    config.setApplicationName(name);
                }
            }

            if (StringUtils.isBlank(config.getThrottlingTier())) {
                System.out.println("Select a throttling tier for the Application: ");
                System.out.println("1 => Allows Unlimited requests");
                System.out.println("2 => Allows 50 requests per minute");
                System.out.println("3 => Allows 20 requests per minute");
                System.out.println("4 => Allows 10 requests per minute");

                String throttlingTier = scanner.next();

                if (throttlingTier.equals("1")) {
                    if (StringUtils.isNotBlank(throttlingTier)) {
                        config.setThrottlingTier("Unlimited");
                    }
                } else if (throttlingTier.equals("2")) {
                    if (StringUtils.isNotBlank(throttlingTier)) {
                        config.setThrottlingTier("50PerMin");
                    }
                } else if (throttlingTier.equals("3")) {
                    if (StringUtils.isNotBlank(throttlingTier)) {
                        config.setThrottlingTier("20PerMin");
                    }
                } else if (throttlingTier.equals("4")) {
                    if (StringUtils.isNotBlank(throttlingTier)) {
                        config.setThrottlingTier("10PerMin");
                    }
                }
            }

            String responseString;
            CloseableHttpResponse response;
            CloseableHttpClient client = null;

            try {
                client = HttpClientGenerator.getHttpClient();
            } catch (UtilException e) {
                String errorMsg = "Error occurred while getting a closableHttpClient for creating";
                log.error(errorMsg, e);
                return apiId;
            }

            String url = config.getStoreUrl() + "/applications";
            HttpPost request = new HttpPost(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + accessToken);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);

            String tier = config.getAPITier();
            String applicationID = config.getApplicationID();

            String jsonBody = "{ \"throttlingTier\": \"" + config.getThrottlingTier() + "\",\n" +
                    "\"name\": \"" + config.getApplicationName() + "\" }";

            request.setEntity(new StringEntity(jsonBody));
            response = client.execute(request);

            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {


            } else if (response.getStatusLine().getStatusCode() == Response.Status.CONFLICT.getStatusCode()) {
                log.error(" Cannot create the Application " + config.getApplicationName() + " already exists");
            } else if (response.getStatusLine().getStatusCode() == Response.Status.CREATED.getStatusCode()) {
                try {

                    System.out.println("Application created successfully..! \n");

                    HttpEntity entity = response.getEntity();
                    responseString = EntityUtils.toString(entity);
                    JSONObject content = (JSONObject) parser.parse(responseString);

                    System.out.println("subscriber: " + (String) content.get("subscriber"));
                    System.out.println("name: " + (String) content.get("name"));
                    System.out.println("Application Id: " + (String) content.get("apiIdentifier"));
                    System.out.println("Throttling Tier: " + (String) content.get("throttlingTier"));
                    System.out.println("Status: " + (String) content.get("status") + "\n");


                } catch (Exception e) {
                    String errorMsg = "Error in creating the application " + config.getApplicationName();
                    log.error(errorMsg, e);
                    throw new APISubscriptionException(errorMsg, e);
                }
            } else if (response.getStatusLine().getStatusCode() == Response.Status.FORBIDDEN.getStatusCode()) {
                log.error("cannot create the application duplicate context exists");
            } else if (response.getStatusLine().getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
                log.error(EntityUtils.toString(response.getEntity()));
            } else if (response.getStatusLine().getStatusCode() == Response.Status.UNAUTHORIZED.getStatusCode()) {
                log.error("Unauthorized request, You cannot create the application since scope validation failed");
            } else {
                log.error("Error occurred while creating the application ");
            }

        } catch (IOException e) {
            log.error("Error occurred while creating the application ", e);
        }
        return config.getApplicationName();
    }

    private static void showExistingApplications(String accessToken) {

        try {
            System.out.println("\nExisting Application....");

            ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
            String responseString;
            CloseableHttpResponse response;
            CloseableHttpClient client = null;

            client = HttpClientGenerator.getHttpClient();
            String url = config.getStoreUrl() + "/applications";
            HttpGet request = new HttpGet(url);

            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                    accessToken);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            response = client.execute(request);

            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                try {
                    HttpEntity entity = response.getEntity();
                    responseString = EntityUtils.toString(entity);
                    JSONObject json = (JSONObject) parser.parse(responseString);
                    JSONArray list = (JSONArray) json.get("list");


                    for (int i = 0; i < list.size(); i++) {
                        JSONObject content = (JSONObject) list.get(i);
                        String name = (String) content.get("name");
                        //    String applicationId = (String) content.get("applicationId");
                        System.out.println("Name: " + name);
                    }

                } catch (IOException e) {
                    String errorMsg = "Error occurred while converting API response to string";
                    log.error(errorMsg, e);
                    throw new APISubscriptionException(errorMsg, e);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.NOT_FOUND.getStatusCode()) {
                String message = "Applications does not exist/ not found ";
                log.warn(message);
                throw new APISubscriptionException(message);
            } else {
                String errorMsg = "Error occurred while retrieving the information of Applications ";
                log.error(errorMsg);
                throw new APISubscriptionException(errorMsg);
            }

        } catch (Exception e) {

        }

    }

    public static void listSubscriptionsByApplication(String consumerCredentials) {

        try {

            ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
            Scanner scanner = new Scanner(System.in, ImportExportConstants.CHARSET);

            if (StringUtils.isBlank(config.getApplicationName())) {
                System.out.print("Enter the name of the Application: ");
                String name = scanner.next();
                if (StringUtils.isNotBlank(name)) {
                    config.setApplicationName(name);
                }
            }

            String accessToken = createAccessToken(consumerCredentials);
            getApplicatonID(config.getApplicationName(), accessToken);

            String responseString;
            CloseableHttpResponse response;
            CloseableHttpClient client = null;

            String applicationId = config.getApplicationID();

            try {
                //Retrieve API subscription- information
                client = HttpClientGenerator.getHttpClient();
                String url = config.getStoreUrl() + "/subscriptions?applicationId=" + applicationId;
                HttpGet request = new HttpGet(url);

                request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                        accessToken);
                request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
                response = client.execute(request);

            } catch (IOException e) {
                String errorMsg = "Error occurred while retrieving details of the application " + applicationId;
                log.error(errorMsg, e);
                throw new APISubscriptionException(errorMsg, e);
            } catch (UtilException e) {
                String errorMsg = "Error occurred while getting a closable http client for execution";
                log.error(errorMsg, e);
                throw new APISubscriptionException(errorMsg, e);
            }
            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                try {
                    HttpEntity entity = response.getEntity();
                    responseString = EntityUtils.toString(entity);

                    //System.out.println("response String... " + responseString);
                    JSONObject json = (JSONObject) parser.parse(responseString);
                    System.out.println("Number of Subscriptions for application " + config.getApplicationName() + " : " + json.get("count"));
                    JSONArray list = (JSONArray) json.get("list");

                    for (int i = 0; i < list.size(); i++) {
                        JSONObject content = (JSONObject) list.get(i);
                        String subscriptionId = (String) content.get("subscriptionId");
                        String apiIdentifier = (String) content.get("apiIdentifier");
                        String tier = (String) content.get("tier");
                        String status = (String) content.get("status");

                        System.out.println("\nSubscription Id: " + subscriptionId);
                        System.out.println("API Identifier Id: " + apiIdentifier);
                        System.out.println("Tier: " + tier);
                        System.out.println("Status: " + status);
                    }

                } catch (IOException e) {
                    String errorMsg = "Error occurred while converting API response to string";
                    log.error(errorMsg, e);
                    throw new APISubscriptionException(errorMsg, e);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.NOT_FOUND.getStatusCode()) {
                String message = "Application " + applicationId + " does not exist/ not found ";
                log.warn(message);
                throw new APISubscriptionException(message);
            } else {
                String errorMsg = "Error occurred while retrieving the subscription-information of application " + applicationId;
                log.error(errorMsg);
                throw new APISubscriptionException(errorMsg);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void removeSubscribedAPI(String consumerCredentials) throws APISubscriptionException {

        try {
            String accessToken = createAccessToken(consumerCredentials);

            showExistingApplications(accessToken);

            ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
            Scanner scanner = new Scanner(System.in, ImportExportConstants.CHARSET);

            if (StringUtils.isBlank(config.getApplicationName())) {
                System.out.print("\nEnter the name of the Application to unsubscribe the API: ");
                String name = scanner.next();
                if (StringUtils.isNotBlank(name)) {
                    config.setApplicationName(name);
                }
            }

            showAllSubscriptions(accessToken);



        } catch (Exception e) {

        }

    }

    private static void showAllSubscriptions(String accessToken) throws APISubscriptionException {

        try {

            ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

            String responseString;
            CloseableHttpResponse response;
            CloseableHttpClient client = null;

            getApplicatonID(config.getApplicationName(),accessToken);

            //Retrieve API subscription- information
            client = HttpClientGenerator.getHttpClient();
            String url = config.getStoreUrl() + "/subscriptions?applicationId="+config.getApplicationID();
            HttpGet request = new HttpGet(url);

            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                    accessToken);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            response = client.execute(request);

            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                try {
                    HttpEntity entity = response.getEntity();
                    responseString = EntityUtils.toString(entity);
                    JSONObject json = (JSONObject) parser.parse(responseString);
                    JSONArray list = (JSONArray) json.get("list");
                    System.out.println("\nApplication Name : " + config.getApplicationName());
                    System.out.println("----------------------------------------------");
                    System.out.println("APIs ");

                    for (int i = 0; i < list.size(); i++) {
                        JSONObject content = (JSONObject) list.get(i);
                        String applicationId = (String) content.get("applicationId");
                   //    String appName = getApplicationName(applicationId, accessToken);
                        String apiIdentifier = (String) content.get("apiIdentifier");
                        System.out.println(apiIdentifier);
                    }

                } catch (IOException e) {
                    String errorMsg = "Error occurred while converting API response to string";
                    log.error(errorMsg, e);
                    throw new APISubscriptionException(errorMsg, e);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.NOT_FOUND.getStatusCode()) {
                String message = "Subscriptions do not exist/ not found ";
                log.warn(message);
                throw new APISubscriptionException(message);
            } else {
                String errorMsg = "Error occurred while retrieving the subscription-information ";
                log.error(errorMsg);
                throw new APISubscriptionException(errorMsg);
            }

        } catch (IOException e) {
            String errorMsg = "Error occurred while retrieving details of the subscription ";
            log.error(errorMsg, e);
            throw new APISubscriptionException(errorMsg, e);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closable http client for execution";
            log.error(errorMsg, e);
            throw new APISubscriptionException(errorMsg, e);
        }

    }

    private static String getApplicationName(String applicationId, String accessToken) {

        String name = null;

        try {

            ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
            String responseString;
            CloseableHttpResponse response;
            CloseableHttpClient client = null;

            client = HttpClientGenerator.getHttpClient();
            String url = config.getStoreUrl() + "/applications/" + applicationId;
            HttpGet request = new HttpGet(url);

            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                    accessToken);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            response = client.execute(request);

            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                try {
                    HttpEntity entity = response.getEntity();
                    responseString = EntityUtils.toString(entity);
                    JSONObject json = (JSONObject) parser.parse(responseString);
                    name = (String) json.get("name");

                } catch (IOException e) {
                    String errorMsg = "Error occurred while converting response to string";
                    log.error(errorMsg, e);
                    throw new APISubscriptionException(errorMsg, e);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.NOT_FOUND.getStatusCode()) {
                String message = "Application " + applicationId + " does not exist/ not found ";
                log.warn(message);
                throw new APISubscriptionException(message);
            } else {
                String errorMsg = "Error occurred while retrieving the information of Application " + applicationId;
                log.error(errorMsg);
                throw new APISubscriptionException(errorMsg);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return name;
    }
}
