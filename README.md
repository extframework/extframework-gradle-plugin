<a id="readme-top"></a>
## ExtensionFramework Gradle Plugin
[![Latest Stable Version](https://img.shields.io/github/v/release/extframework/extframework-gradle-plugin?include_prereleases)](https://github.com/extframework/extframework-gradle-plugin)
[![Top Language](https://img.shields.io/github/languages/top/extframework/extframework-gradle-plugin)](https://github.com/extframework/extframework-gradle-plugin)
[![Last Commit](https://img.shields.io/github/last-commit/extframework/extframework-gradle-plugin)](https://github.com/extframework/extframework-gradle-plugin)
[![Issues Open](https://img.shields.io/github/issues/extframework/extframework-gradle-plugin)](https://github.com/extframework/extframework-gradle-plugin)

[![Supported ](https://img.shields.io/badge/Mac-Supported-Green)](https://github.com/extframework/example-extension)
[![Supported ](https://img.shields.io/badge/Windows-BUG-ff0000)](https://github.com/extframework/example-extension)
[![Supported ](https://img.shields.io/badge/Linux-Unknown-aaaaaa)](https://github.com/extframework/example-extension)

## Getting Started
The plugin is used in the build.gradle file and its primary section is 'extension': 
```
extension {
    model {
        name = "example-extension"
    }

    extensions {
    }

    metadata {
        name = "A human readable name (not camel/snake case)"
        developers.add("Durgan McBroom")
        description.set("Whatever you want here")
    }

    partitions {
        main {
            extensionClass = "org.example.Example"
            dependencies {
            }
        }
        version("latest") {
            dependencies {
                archives()
                commonUtil()

                minecraft("1.21")
                coreApi()
            }
            mappings = MinecraftMappings.mojang

            supportVersions("1.21")
        }

        version("1.19.2") {
            mappings = MinecraftMappings.mojang
            dependencies {
                minecraft("1.19.2")
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
                coreApi()
            }

            supportVersions("1.18", "1.19.2")
        }
    }
}
```
The most important section is the "partitions" section.  There are three possible entries:
* **main** - which is required and points to the main entrypoint, via the 'extensionClass' and brings in the kotlin stdlib as a dependency
* **version(versionStr)** - which maps a partition directory to a MC version, brings in dependencies for that version and specifies the 
deobfuscation mappings. coreApi() is always needed here as a dependency.  It takes a version string which must map the name of the 
partition directory (under 'src').  Either way, a 'supportVersions' enumeration is needed which must map to specific MC versions.
Currently there is no range specification, each version must be explicitly listed
* **tweaker** - This is not commonly used but can be used to change how the extframework run.  One could add mew dependency systems, new types of partitions,
new types of MC mappings or new types of mixins.

The other sections are either self-explanatory or TBD.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Documentation
Further documention can be found at:

* Mixins in the Extension Loader project [https://github.com/extframework/ext-loader](https://github.com/extframework/ext-loader)
* Full Example project [https://github.com/extframework/example-extension](https://github.com/extframework/example-extension)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

### Contact

<ul>
  <li> Discord: @durganmcbroom  or  <a href="https://discord.gg/3fP4N27JPH">@extframework discord</a></li>
  <li> Linkedin: https://www.linkedin.com/in/durganmcbroom/ </li> 
  <li> Email: durganmcbroom@gmail.com </li>
</ul>

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- CREDITS -->
## Credits

Durgan McBroom

[![GitHub Badge](https://img.shields.io/badge/GitHub-100000?style=for-the-badge&logo=github&logoColor=white)](https://github.com/durganmcbroom)
[![LinkedIn Badge](https://img.shields.io/badge/LinkedIn-0077B5?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/durganmcbroom/)
