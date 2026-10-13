package com.canreadit.media.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.canreadit.TestcontainersConfiguration;
import com.canreadit.media.AssetKind;
import com.canreadit.media.ImageVariant;
import com.canreadit.media.MediaUploads;
import com.canreadit.media.MediaUrls;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.Ids;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    void storesCoversAsWebpInSeveralSizes() throws Exception {
        byte[] png = TestImages.encode("png", 1600, 2400, 0x00D1B2);

        var id = uploads.storePublicImage(AssetKind.COVER, png);
        var original = urls.resolve(List.of(id), ImageVariant.ORIGINAL).get(id);
        var medium = urls.resolve(List.of(id), ImageVariant.MEDIUM).get(id);
        var small = urls.resolve(List.of(id), ImageVariant.SMALL).get(id);

        assertThat(original).isNotNull();
        assertThat(original.width()).isEqualTo(1200);
        assertThat(original.height()).isEqualTo(1800);
        assertThat(original.url()).matches(".*/canreadit-media/covers/[0-9a-f]{2}/[0-9a-f]{64}\\.webp");
        assertThat(medium).isNotNull();
        assertThat(medium.width()).isEqualTo(600);
        assertThat(medium.height()).isEqualTo(900);
        assertThat(medium.url()).endsWith("-600.webp");
        assertThat(small).isNotNull();
        assertThat(small.width()).isEqualTo(300);
        assertThat(small.url()).endsWith("-300.webp");

        HttpResponse<byte[]> response = fetch(original.url());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("image/webp");
        assertThat(ImageInspector.inspect(response.body()).width()).isEqualTo(1200);
        assertThat(fetch(small.url()).statusCode()).isEqualTo(200);
    }

    @Test
    void fitsPagesToTheReaderWidthAndKeepsSmallerOnes() {
        var wide = uploads.storePublicImage(AssetKind.PAGE, TestImages.encode("jpg", 2160, 3000, 0x223344));
        var narrow = uploads.storePublicImage(AssetKind.PAGE, TestImages.encode("png", 800, 1200, 0x445566));

        var refs = urls.resolve(List.of(wide, narrow), ImageVariant.MEDIUM);
        assertThat(refs.get(wide).width()).isEqualTo(1080);
        assertThat(refs.get(wide).height()).isEqualTo(1500);
        // Pages have one size; any variant resolves to it.
        assertThat(refs.get(narrow).width()).isEqualTo(800);
        assertThat(refs.get(narrow).url()).endsWith(".webp").doesNotContain("-800");
    }

    @Test
    void rejectsWhatIsNotARasterImage() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> uploads.storePublicImage(AssetKind.PAGE, svg))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo("unsupported_image"));
    }

    private static HttpResponse<byte[]> fetch(String url) throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        }
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
