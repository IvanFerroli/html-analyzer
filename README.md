# HtmlAnalyzer (Java 17)

CLI tool that fetches a URL (HTTP/HTTPS) and analyzes a simplified HTML document where each line is exactly one of:

- an opening tag: `<tag>`
- a closing tag: `</tag>`
- a text line

The program outputs exactly one of:

- the deepest text line (first occurrence in case of ties)
- `malformed HTML`
- `URL connection error`

## Requirements

- Java 17+

## Build

```bash
javac HtmlAnalyzer.java
```
