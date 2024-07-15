package com.sa1zer.crpt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

public class CrptApi {

    private static final String DOCUMENT_CREATE_API = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());;

    private final Semaphore semaphore;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if(requestLimit <= 0) throw new IllegalArgumentException("requestLimit must be positive!");

        semaphore = new Semaphore(requestLimit);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        //сброс лимитов через 1 единицу переданного времени
        executor.scheduleAtFixedRate(() -> semaphore.release(requestLimit - semaphore.availablePermits()),
                0, 1, timeUnit);
    }

    public void createDocument(String apiUrl, Document document, String signature) throws InterruptedException, IOException {
        //попытка захватить монитор
        if(!semaphore.tryAcquire()) {
            System.out.println("Request limit exceeded");
            return;
        }

        //отправка запрос
        String response = sendRequest(apiUrl, document, signature);

        System.out.println("Response: " + response);

        //освобождение
        semaphore.release();
    }

    private String sendRequest(String apiUrl, Document document, String signature) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(document)))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if(response.statusCode() / 100 == 2) {
            System.out.println("Success!");
        } else {
            System.out.println(String.format("Error while creating document, response status: %s", response.statusCode()));
        }

        return response.body();
    }

    record Document(Description description,
                    @JsonProperty(value = "doc_id") String docId,
                    @JsonProperty(value = "doc_status") String docStatus,
                    @JsonProperty(value = "doc_type") String docType,
                    Boolean importRequests,
                    @JsonProperty(value = "owner_inn") String ownerInn,
                    @JsonProperty(value = "participant_inn") String participantInn,
                    @JsonProperty("producer_inn") String producerInn,
                    @JsonProperty("production_date") @JsonFormat(pattern = "yyyy-MM-dd", shape = JsonFormat.Shape.STRING) LocalDate productionDate,
                    @JsonProperty("production_type") String productionType,
                    List<Product> products,
                    @JsonProperty("reg_date") @JsonFormat(pattern = "yyyy-MM-dd", shape = JsonFormat.Shape.STRING) LocalDate regDate,
                    @JsonProperty("reg_number") String regNumber
    ) {}

    record Description(String participantInn) {}

    record Product(@JsonProperty("certificate_document") String certificateDocument,
                   @JsonProperty("certificate_document_date") @JsonFormat(pattern = "yyyy-MM-dd", shape = JsonFormat.Shape.STRING) LocalDate certDate,
                   @JsonProperty("certificate_document_number") String certNumber,
                   @JsonProperty(value = "owner_inn") String ownerInn,
                   @JsonProperty("producer_inn") String producerInn,
                   @JsonProperty("production_date") @JsonFormat(pattern = "yyyy-MM-dd", shape = JsonFormat.Shape.STRING) LocalDate productionDate,
                   @JsonProperty("tnved_code") String tnvedCode,
                   @JsonProperty("uit_code") String uitCode,
                   @JsonProperty("uitu_code") String uituCode) {}

    public static void main(String[] args) throws IOException, InterruptedException {
        Executor executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 1);

        Document document = new Document(new Description("123123"),
                "1", "awdawd", "type", true, "123123", "123123",
                "123123", LocalDate.now(), "type",
                List.of(new Product("string", LocalDate.now(), "123", "123123", "123123", LocalDate.now(), "123", "123", "123")),
                LocalDate.now(), "123");

        executor.execute(() -> {
            try {
                crptApi.createDocument(DOCUMENT_CREATE_API, document, "megasignature");
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        executor.execute(() -> {
            try {
                crptApi.createDocument(DOCUMENT_CREATE_API, document, "megasignature");
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        executor.execute(() -> {
            try {
                crptApi.createDocument(DOCUMENT_CREATE_API, document, "megasignature");
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(1000);

        executor.execute(() -> {
            try {
                crptApi.createDocument(DOCUMENT_CREATE_API, document, "megasignature");
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        executor.execute(() -> {
            try {
                crptApi.createDocument(DOCUMENT_CREATE_API, document, "megasignature");
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
