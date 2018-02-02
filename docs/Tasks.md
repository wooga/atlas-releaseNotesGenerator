Tasks
=====

The plugin will add the following tasks to the build.

| Task name            | Depends on          | Type                                                            | Description                                                                                 |
| -------------------- | ------------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| appendReleaseNotes   |                     | `wooga.gradle.releaseNotesGenerator.tasks.GenerateReleaseNotes` | Generates the release notes for the last release and appends it to the end of provided file |
| updateReleaseNotes   | appendLatestRelease | `wooga.gradle.releaseNotesGenerator.tasks.UpdateReleaseNotes`   | Commits release notes file to github                                                        |
| generateReleaseNotes |                     | `wooga.gradle.releaseNotesGenerator.tasks.GenerateReleaseNotes` | Generates the release notes from the complete git history                                   |
