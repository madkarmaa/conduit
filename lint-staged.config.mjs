// Spotless formats the whole project, including the files that bun fmt handles, so the task takes
// no file names. lint-staged adds the formatted versions of the staged files to the commit.
export default {
    "*": () => "./gradlew spotlessApply",
};
