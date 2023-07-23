const {DownloaderHelper} = require('node-downloader-helper');
const extract = require('extract-zip');
const fs = require('fs');
const path = require('path');

const url = "https://archive.org/download/atbp-20150519/atbp-20150519-full.zip";
const dl = new DownloaderHelper(url, "./static/");

dl.on('end', () => {
  console.log("Download completed, extracting...");
  try {
    extract("./static/atbp-20150519-full.zip", { dir: path.resolve("./static/") });
    console.log("Extraction completed.");
  } catch (err) {
    console.error("Extraction failed: " + err);
  } finally {
    fs.rmSync("./static/atbp-20150519-full.zip");
    console.log("Cleaned up.");
  }
});

dl.on('error', (err) => console.error("Asset download failed - check Internet connection.", err));
dl.on('start', () => {console.log("Downloading game assets... this may take a while.")});

if(!fs.existsSync("static/CNChampions.unity3d") || !fs.existsSync("static/assets")) {
  dl.start().catch(err => console.error(err));
} else {
  console.log("Asset files already present, skipping download.");
}
