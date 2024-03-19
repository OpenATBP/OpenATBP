// https://eslint.org/docs/user-guide/configuring
module.exports = {
  extends: ['airbnb-base', 'airbnb-typescript/base'],
  rules: {
    // Maximum line length (Unicode characters).
    'max-len': ['warn', {
      code: 100,
      tabWidth: 2,
      ignoreComments: true,
      ignoreTrailingComments: true,
      ignoreUrls: true,
      ignoreStrings: true,
      ignoreTemplateLiterals: true,
      ignoreRegExpLiterals: true,
    }],
    // Disallow calls to methods of the console object.
    'no-console': 'warn',
    // Reduce the scrolling required when reading through code.
    'no-multiple-empty-lines': ['error', { max: 1, maxEOF: 1 }],
    // Cyclomatic complexity threshold.
    complexity: ['error', { max: 15 }],
  },
  parserOptions: {
    project: './tsconfig.json',
  },
};
