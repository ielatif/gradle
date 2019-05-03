/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class IncrementalTasksIntegrationTest extends AbstractIntegrationSpec {

    def "consecutively failing task has correct up-to-date status and failure"() {
        buildFile << """
            task foo {
                outputs.file("out.txt")
                doLast {
                    if (project.file("out.txt").exists()) {
                        throw new RuntimeException("Boo!")
                    }
                    project.file("out.txt") << "xxx"
                }
            }
        """

        expect:
        succeeds "foo"

        when:
        file("out.txt") << "force rerun"
        fails "foo"
        then:
        failureHasCause "Boo!"

        when:
        fails "foo", "--info"
        then:
        failureHasCause "Boo!"
        output.contains "Task has failed previously."
        //this exposes an issue we used to have with in-memory cache.
    }

    @Unroll
    def "incremental task after previous failure #description"() {
        file("src/input.txt") << "input"
        buildFile << """
            class IncrementalTask extends DefaultTask {
                @InputDirectory File sourceDir
                @OutputDirectory File destinationDir
                
                @TaskAction
                void process(IncrementalTaskInputs inputs) {
                    project.file("\$destinationDir/output.txt").text = "output"
                    if (project.hasProperty("modifyOutputs")) {
                        switch (project.property("modifyOutputs")) {
                            case "add":
                                project.file("\$destinationDir/output-\${System.currentTimeMillis()}.txt").text = "output"
                                break
                            case "change":
                                project.file("\$destinationDir/output.txt").text = "changed output -- \${System.currentTimeMillis()}"
                                break
                            case "remove":
                                project.delete("\$destinationDir/output.txt")
                                break
                        }
                    }

                    if (project.hasProperty("expectIncremental")) {
                        def expectIncremental = Boolean.parseBoolean(project.property("expectIncremental"))
                        assert inputs.incremental == expectIncremental
                    }

                    if (project.hasProperty("fail")) {
                        throw new RuntimeException("Failure")
                    }
                }
            }

            task incrementalTask(type: IncrementalTask) {
                sourceDir = file("src")
                destinationDir = file("build")
            }
        """

        succeeds "incrementalTask", "-PexpectIncremental=false"

        file("src/input-change.txt") << "input"
        fails "incrementalTask", "-PexpectIncremental=true", "-PmodifyOutputs=$modifyOutputs", "-Pfail"

        expect:
        succeeds "incrementalTask", "-PexpectIncremental=$incremental"

        where:
        modifyOutputs | incremental | description
        "add"         | false       | "with additional outputs is fully rebuilt"
        "change"      | false       | "with changed outputs is fully rebuilt"
        "remove"      | false       | "with removed outputs is fully rebuilt"
        "none"        | true        | "with unmodified outputs is executed as incremental"
    }

    @Issue("https://github.com/gradle/gradle/issues/9320")
    def "incremental task with NAME_ONLY input detects correct input change"() {
        def inputA = file("input/a/foo.txt")
        def inputB = file("input/b/foo.txt")

        inputA.text = ""
        inputB.text = ""

        buildFile << """
            class IncrementalTask extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.NAME_ONLY)
                FileCollection inputFiles = project.files('input/a/foo.txt', 'input/b/foo.txt')
            
                @TaskAction
                def taskAction(IncrementalTaskInputs inputs) {
                    println 'outOfDate:'
                    inputs.outOfDate { println "\${it.file} - \${it.file.exists()}" }
                }
            }
            
            task incrementalTask(type: IncrementalTask) {}
        """

        run "incrementalTask"

        when:
        inputA.text = "changed"
        run "incrementalTask", "--info"
        then:
        output.contains "Input property 'inputFiles' file ${inputA.absolutePath} has changed."

        when:
        inputA.text = ""
        run "incrementalTask", "--info"
        then:
        output.contains "Input property 'inputFiles' file ${inputA.absolutePath} has changed."
    }

    def "incremental task with NAME_ONLY detects correct deleted file"() {
        def inputA = file("input/a/foo.txt")
        def inputB = file("input/b/foo.txt")
        def inputC = file("input/c/foo.txt")

        inputA.text = ""
        inputB.text = ""
        inputC.text = ""

        buildFile << """
            class IncrementalTask extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.NAME_ONLY)
                FileCollection inputFiles = project.files('input/a/foo.txt', 'input/b/foo.txt', 'input/c/foo.txt')
            
                @TaskAction
                def taskAction(IncrementalTaskInputs inputs) {
                    println 'outOfDate:'
                    inputs.outOfDate { println "\${it.file} - \${it.file.exists()}" }
                }
            }
            
            task incrementalTask(type: IncrementalTask) {}
        """

        run "incrementalTask"

        when:
        inputB.delete()
        inputC.delete()
        run "incrementalTask", "--info"
        then:
        output.contains "Input property 'inputFiles' file ${inputA.absolutePath} has changed."
    }
}
