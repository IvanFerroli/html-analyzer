import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlAnalyzer {

  private static final String OUT_MALFORMED = "malformed HTML";
  private static final String OUT_URL_ERROR = "URL connection error";

  private enum Kind {
    OK_TEXT,
    MALFORMED_HTML,
    URL_CONNECTION_ERROR
  }

  private static final class Result {
    final Kind kind;
    final String text;

    Result(Kind kind, String text) {
      this.kind = kind;
      this.text = text;
    }

    static Result okText(String text) {
      return new Result(Kind.OK_TEXT, text);
    }

    static Result malformed() {
      return new Result(Kind.MALFORMED_HTML, null);
    }

    static Result urlError() {
      return new Result(Kind.URL_CONNECTION_ERROR, null);
    }
  }

  private static final class FetchException extends Exception {
    FetchException(String msg, Throwable cause) {
      super(msg, cause);
    }

    FetchException(String msg) {
      super(msg);
    }
  }

  private static final Pattern OPEN_TAG = Pattern.compile("^<([A-Za-z][A-Za-z0-9]*)>$");
  private static final Pattern CLOSE_TAG = Pattern.compile("^</([A-Za-z][A-Za-z0-9]*)>$");

  public static void main(String[] args) {
    if (args.length != 1) {
      System.exit(1);
      return;
    }

    Result result;
    try {
      List<String> lines = fetchHtmlLines(args[0]);
      result = analyze(lines);
    } catch (FetchException e) {
      result = Result.urlError();
    } catch (Throwable t) {
      // Never leak stacktrace; treat unexpected issues as malformed.
      result = Result.malformed();
    }

    printAndExit(result);
  }

  private static void printAndExit(Result result) {
    if (result == null) {
      System.out.println(OUT_MALFORMED);
      return;
    }

    switch (result.kind) {
      case OK_TEXT:
        System.out.println(result.text == null ? OUT_MALFORMED : result.text);
        break;
      case URL_CONNECTION_ERROR:
        System.out.println(OUT_URL_ERROR);
        break;
      case MALFORMED_HTML:
      default:
        System.out.println(OUT_MALFORMED);
        break;
    }
  }

  private static List<String> fetchHtmlLines(String url) throws FetchException {
    try {
      HttpClient client = HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(Duration.ofSeconds(10))
          .build();

      URI uri = URI.create(url);

      // Autograder-safe: accept only absolute http(s) URLs.
      if (!uri.isAbsolute()) throw new FetchException("Non-absolute URL");
      String scheme = uri.getScheme();
      if (scheme == null) throw new FetchException("Missing URL scheme");
      String s = scheme.toLowerCase();
      if (!s.equals("http") && !s.equals("https")) throw new FetchException("Unsupported URL scheme: " + scheme);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(uri)
          .timeout(Duration.ofSeconds(20))
          .GET()
          .header("User-Agent", "HtmlAnalyzer/1.0")
          .build();

      HttpResponse<String> response = client.send(
          request,
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
      );

      int status = response.statusCode();
      if (status < 200 || status > 299) {
        throw new FetchException("Non-2xx status: " + status);
      }

      String body = response.body();
      if (body == null) body = "";

      // Keep empty lines here; analyzer ignores blanks/indentation.
      String[] parts = body.split("\\R", -1);
      return Arrays.asList(parts);

    } catch (IllegalArgumentException e) {
      throw new FetchException("Bad URL", e);
    } catch (Exception e) {
      throw new FetchException("Fetch failed", e);
    }
  }

  private static Result analyze(List<String> lines) {
    if (lines == null) return Result.malformed();

    Deque<String> stack = new ArrayDeque<>();
    String bestText = null;
    int maxDepth = -1;

    for (String raw : lines) {
      if (raw == null) continue;

      String line = raw.trim();
      if (line.isEmpty()) continue;

      if (line.startsWith("<")) {
        Matcher mOpen = OPEN_TAG.matcher(line);
        if (mOpen.matches()) {
          stack.push(mOpen.group(1));
          continue;
        }

        Matcher mClose = CLOSE_TAG.matcher(line);
        if (mClose.matches()) {
          String tag = mClose.group(1);
          if (stack.isEmpty()) return Result.malformed();
          if (!tag.equals(stack.peek())) return Result.malformed();
          stack.pop();
          continue;
        }

        // Starts with '<' but is not <tag> nor </tag> (attributes, self-closing, etc.)
        return Result.malformed();
      }

      // Text line: depth is current nesting.
      int depth = stack.size();
      if (depth > maxDepth) {
        maxDepth = depth;
        bestText = line; // tie-break: do not replace on equal depth
      }
    }

    if (!stack.isEmpty()) return Result.malformed();
    if (bestText == null) return Result.malformed();

    return Result.okText(bestText);
  }
}
