# Publish artifacts to maven central repository

To publish your artifacts into maven central repository, you have to prepare your java project repo accordingly:
* Go to https://s01.oss.sonatype.org/#profile;User%20Token in your profile to get token-username and token-password for
  `settings.xml` file, servers/server section
* Create JIRA and requires to create repository for you.
  After JIRA gets created, you must create an empty public repository like these:
  * https://github.com/daggerok/OSSRH-81403
  * https://github.com/daggerok/OSSRH-90442
* Update your [pom.xml](pom.xml) file with next information:
  ```xml
    <name>${project.groupId}:${project.artifactId}</name>
    <description>${project.groupId}:${project.artifactId}</description>
    <url>https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter</url>
    <developers>
        <developer>
            <name>Maksim Kostromin</name>
            <email>daggerok@gmail.com</email>
            <organization>Sets of open source Maksim Kostromin aka daggerok projects</organization>
            <organizationUrl>https://github.com/daggerok/</organizationUrl>
        </developer>
    </developers>
    <organization>
        <name>Maksim Kostromin aka daggerok open source projects sets</name>
        <url>https://github.com/daggerok/</url>
    </organization>
    <licenses>
        <license>
            <name>MIT License</name>
            <distribution>repo</distribution>
            <!-- <url>https://opensource.org/licenses/MIT</url> -->
            <url>https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter/blob/master/LICENSE</url>
        </license>
    </licenses>
    <scm>
        <developerConnection>scm:git:git@github.com:daggerok/distributed-lock-mongodb-spring-boot-starter.git</developerConnection>
        <connection>scm:git:https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter.git</connection>
        <url>https://github.com/daggerok/distributed-lock-mongodb-spring-boot-starter</url>
        <!-- <tag>master</tag> -->
        <tag>HEAD</tag>
    </scm>
    <properties>
        <nexus-staging-maven-plugin.version>1.6.13</nexus-staging-maven-plugin.version>
        <maven-release-plugin.version>2.5.3</maven-release-plugin.version>
        <maven-gpg-plugin.version>3.0.1</maven-gpg-plugin.version>
        <gpg.skip>true</gpg.skip>
        <javadoc.opts/>
    </properties>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
                <executions>
                    <execution>
                        <phase>generate-sources</phase>
                        <id>attach-javadocs</id>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <doclint>none</doclint>
                    <failOnError>false</failOnError>
                    <failOnWarnings>false</failOnWarnings>
                    <additionalJOption>${javadoc.opts}</additionalJOption>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-source-plugin</artifactId>
                <executions>
                    <execution>
                        <phase>generate-sources</phase>
                        <id>attach-sources</id>
                        <goals>
                            <goal>jar-no-fork</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-gpg-plugin</artifactId>
                <version>${maven-gpg-plugin.version}</version>
                <configuration>
                    <skip>${gpg.skip}</skip>
                </configuration>
                <executions>
                    <execution>
                        <phase>verify</phase>
                        <id>sign-artifacts</id>
                        <goals>
                            <goal>sign</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
    <reporting>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
                <configuration>
                    <minmemory>128m</minmemory>
                    <maxmemory>512</maxmemory>
                </configuration>
            </plugin>
        </plugins>
    </reporting>
    <profiles>
        <profile>
            <id>java8-doclint-disabled</id>
            <activation>
                <jdk>[1.8,)</jdk>
            </activation>
            <properties>
                <javadoc.opts>-Xdoclint:none</javadoc.opts>
            </properties>
        </profile>
        <profile>
            <id>read gpg.passphrase system property</id>
            <!--
                This profile will pick `gpg.passphrase` java system property to configure maven-gpg-plugin passphrase implicitly
                ./mvnw -Dgpg.passphrase=...
            -->
            <activation>
                <property>
                    <name>gpg.passphrase</name>
                </property>
            </activation>
            <properties>
                <gpg.skip>false</gpg.skip>
            </properties>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-gpg-plugin</artifactId>
                        <version>${maven-gpg-plugin.version}</version>
                        <executions>
                            <execution>
                                <id>sign-artifacts</id>
                                <phase>verify</phase>
                                <goals>
                                    <goal>sign</goal>
                                </goals>
                                <configuration>
                                    <passphrase>${gpg.passphrase}</passphrase>
                                    <gpgArguments>
                                        <arg>--pinentry-mode</arg>
                                        <arg>loopback</arg>
                                    </gpgArguments>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
        <profile>
            <id>read GPG_PASSPHRASE environment variable</id>
            <!--
                This profile will pick `GPG_PASSPHRASE` environment variable to configure maven-gpg-plugin passphrase implicitly
                GPG_PASSPHRASE=... ./mvnw ...
            -->
            <activation>
                <property>
                    <name>env.GPG_PASSPHRASE</name>
                </property>
            </activation>
            <properties>
                <gpg.skip>false</gpg.skip>
            </properties>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-gpg-plugin</artifactId>
                        <version>${maven-gpg-plugin.version}</version>
                        <executions>
                            <execution>
                                <id>sign-artifacts</id>
                                <phase>verify</phase>
                                <goals>
                                    <goal>sign</goal>
                                </goals>
                            </execution>
                        </executions>
                        <configuration>
                            <passphrase>${env.GPG_PASSPHRASE}</passphrase>
                            <!-- Prevent `gpg` from using pinentry programs -->
                            <gpgArguments>
                                <arg>--pinentry-mode</arg>
                                <arg>loopback</arg>
                            </gpgArguments>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
        <profile>
            <id>central-release</id>
            <!--
                This profile mainly must be used to release project artifacts to central maven repository
                Prerequisites are:
                    - gpg install, private key with passphrase generated
                    - maven pom.xml required plugins added and configured with proper profiles
                Workflow:
                    - Let's say your version is x.y.z-SNAPSHOT
                        ./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout
                    - Then you can deploy snapshot version to maven central just like so:
                        GPG_PASSPHRASE=... ./mvnw -P central-release clean deploy
                    - When testing of deployed x.y.z-SNAPSHOT is done, you can change version to released: x.y.z-SNAPSHOT -> x.y.z
                        ./mvnw build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.incrementalVersion}
                    - If something goes wrong, you can simply revert last change with help of previously created pom.xml.versionsBackup files:
                        ./mvnw build-helper:parse-version versions:revert
                    - Otherwise commit, this will remove all generated pom.xml.versionsBackup files:
                        ./mvnw build-helper:parse-version versions:commit
                    - Let's commit release version with these commands:
                        VERSION=`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`
                        git add . ; git commit -am "Release $VERSION version manually." ; git push origin master
                    - And release x.y.z version artifacts to maven central:
                        GPG_PASSPHRASE=... ./mvnw -P central-release clean deploy
                    - It's time to create and push GitHub tag released version:
                        git tag $VERSION ; git push origin $VERSION
                    - Finally create next SNAPSHOT version for future development iteration: x.y.z -> x.y.(z+1)-SNAPSHOT
                        ./mvnw build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.nextIncrementalVersion}-SNAPSHOT
                        ./mvnw build-helper:parse-version versions:commit
                        VERSION=`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`
                        git add . ; git commit -am "Start next $VERSION development iteration." ; git push origin master
                see: https://central.sonatype.org/publish/publish-maven/#nexus-staging-maven-plugin-for-deployment-and-release
            -->
            <distributionManagement>
                <snapshotRepository>
                    <id>ossrh</id>
                    <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
                </snapshotRepository>
            </distributionManagement>
            <build>
                <defaultGoal>clean deploy</defaultGoal>
                <plugins>
                    <plugin>
                        <groupId>org.sonatype.plugins</groupId>
                        <artifactId>nexus-staging-maven-plugin</artifactId>
                        <version>${nexus-staging-maven-plugin.version}</version>
                        <extensions>true</extensions>
                        <configuration>
                            <serverId>ossrh</serverId>
                            <nexusUrl>https://s01.oss.sonatype.org/</nexusUrl>
                            <autoReleaseAfterClose>true</autoReleaseAfterClose>
                            <tags>
                                <localUsername>${env.USER}</localUsername>
                                <javaVersion>${java.version}</javaVersion>
                            </tags>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
  ```
* Create or update your `~/.m2/settings.xml` file accordingly to [.mvn/settings.xml](.mvn/settings.xml):
  ```xml
  <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                                https://maven.apache.org/xsd/settings-1.0.0.xsd">
      <servers>
          <server>
              <id>ossrh</id>
              <username>${sonatype username}</username>
              <password>${sonatype password}</password>
          </server>
      </servers>
  </settings>
  ```
* Create [.bin](.bin) scripts to simplify release process
* Add maven central badge to your `README.md` file:
  ```markdown
  [![Maven Central](https://img.shields.io/maven-central/v/io.github.daggerok/distributed-lock-mongodb-spring-boot-starter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.daggerok%22%20AND%20a:%22distributed-lock-mongodb-spring-boot-starter%22)
  ```
* Finally, when everything is ready for release run:
  * next command to build and upload SNAPSHOT version:
    ```bash
    GPG_PASSPHRASE=YourGpgPassword bash .bin/central-snapshot.sh -DskipTests
    ```
  * next command to build and upload release version:
    ```bash
    GPG_PASSPHRASE=YourGpgPassword bash .bin/central-release.sh -DskipTests
    ```
  * finally, in case if you want to release a patch or hotfix to your previous release and do not break release
    version numbers sequence, use qualifier, but before revert to previous SNAPSHOT with next two commands:
    ```bash
    bash .bin/update-version.sh 3.0.6-SNAPSHOT
    QUALIFIER=4 GPG_PASSPHRASE=YourGpgPassword bash .bin/central-release.sh -DskipTests
    ```
