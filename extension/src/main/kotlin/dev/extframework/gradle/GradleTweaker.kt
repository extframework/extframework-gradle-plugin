package dev.extframework.gradle

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.gradle.partition.GradlePartitionLoader
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.extract
import dev.extframework.tooling.api.environment.partitionLoadersAttrKey
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker

public class GradleTweaker : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
        environment[partitionLoadersAttrKey].extract()
            .container
            .register("gradle", GradlePartitionLoader())
    }
}