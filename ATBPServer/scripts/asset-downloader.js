const { DownloaderHelper } = require('node-downloader-helper');
const extract = require('extract-zip');
const fs = require('fs');
const path = require('path');

const url =
  'https://archive.org/download/openatbp-20240808/openatbp-20240808-full.zip';
const dl = new DownloaderHelper(url, './static/');

dl.on('end', () => {
  console.log('Download completed, extracting...');
  try {
    extract('./static/openatbp-20240808-full.zip', {
      dir: path.resolve('./static/'),
    }).then(() => {
      console.log('Extraction completed.');
      fs.rmSync('./static/openatbp-20240808-full.zip');
      console.log('Cleaned up.');
    });
  } catch (err) {
    console.error('Extraction failed: ' + err);
  }
});

dl.on('error', (err) => {
  console.error('Asset download failed - check Internet connection.', err);
});
dl.on('start', () => {
  console.log('Downloading game assets... this may take a while.');
});

if (
  !fs.existsSync('static/CNChampions.unity3d') ||
  !fs.existsSync('static/assets')
) {
  dl.start().catch((err) => console.error(err));
} else {
  console.log('Asset files already present, skipping download.');
}
