// https://babeljs.io/docs/en/options
module.exports = function (api) {
  api.cache(true);

  return {
    // Some options (sourceMaps, code, ast, etc.) can only be passed through cli arguments.
    // Ignore processing for files. Accepts globs.
    ignore: [
      'node_nodules/**/*',
    ],
    // An array of presets to activate when processing.
    presets: [
      [
        '@babel/preset-env',
        {
          targets: {
            node: '20.11.0',
          },
        },
      ],
      '@babel/preset-typescript',
    ],
    // An array of plugins to activate when processing.
    plugins: [],
    // Omits unnecessary characters and whitespace. Includes compact: true.
    minified: false,
    // Include comments in the output code.
    comments: true,
    // Reconfigure options on specific environments.
    env: {
      production: {
        minified: true,
        comments: false,
      },
      test: {
        // plugins: ['istanbul'],
      },
    },
  };
}
