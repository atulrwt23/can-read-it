package com.canreadit.media.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.TestcontainersConfiguration;
import com.canreadit.media.AssetKind;
import com.canreadit.media.ImageVariant;
import com.canreadit.media.MediaUploads;
import com.canreadit.media.MediaUrls;
import com.canreadit.shared.Ids;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MediaServiceIT {

    @Autowired
    MediaUploads uploads;

    @Autowired
    MediaUrls urls;

    @Autowired
    S3Client s3;

    @BeforeEach
    void createBucket() {
        try {
            s3.headBucket(b -> b.bucket(TestcontainersConfiguration.BUCKET));
        } catch (NoSuchBucketException e) {
            s3.createBucket(b -> b.bucket(TestcontainersConfiguration.BUCKET));
        }
    }

    @Test
    void storesAndResolvesPublicImages() throws Exception {
        byte[] png = TestImages.encode("png", 12, 34, 0x00D1B2);

        var id = uploads.storePublicImage(AssetKind.COVER, png);
        var ref = urls.resolve(List.of(id), ImageVariant.ORIGINAL).get(id);

        assertThat(ref).isNotNull();
        assertThat(ref.width()).isEqualTo(12);
        assertThat(ref.height()).isEqualTo(34);
        assertThat(ref.url()).matches(".*/canreadit-media/covers/[0-9a-f]{2}/[0-9a-f]{64}\\.png");

        HttpResponse<byte[]> response;
        try (var http = HttpClient.newHttpClient()) {
            response = http.send(
                    HttpRequest.newBuilder(URI.create(ref.url())).build(), HttpResponse.BodyHandlers.ofByteArray());
        }
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(png);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("image/png");
    }

    @Test
    void deduplicatesIdenticalContent() {
        byte[] png = TestImages.encode("png", 5, 5, 0x123456);

        assertThat(uploads.storePublicImage(AssetKind.PAGE, png))
                .isEqualTo(uploads.storePublicImage(AssetKind.PAGE, png));
    }

    @Test
    void omitsUnknownIds() {
        assertThat(urls.resolve(List.of(Ids.newId()), ImageVariant.ORIGINAL)).isEmpty();
        assertThat(urls.resolve(List.of(), ImageVariant.ORIGINAL)).isEmpty();
    }
}
