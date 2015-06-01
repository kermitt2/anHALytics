# AnHALytics

AnHALytics is a project aiming at creating an analytic platform for the [HAL research archive](https://hal.archives-ouvertes.fr)(mainly), exploring various analytic aspects such as search/discovery, activity and collaboration statistics, trend/technology maps, knowledge and data visualization. The project is supported by an [ADT Inria](http://www.inria.fr/en/research/research-teams/technological-development-at-inria) grant and good will :). 

### Prerequisites:
###### 1. Java/Maven
[JAVA](https://java.com/en/download/manual_java7.jsp) +
[Maven](https://maven.apache.org)
###### 2. Grobid (GeneRation Of BIbliographic Data)
The project is based on the usage of [Grobid](https://github.com/grobid/grobid) for extracting structered ([TEI](http://www.tei-c.org/Guidelines/)) data. [Grobid](https://github.com/grobid/grobid) is a machine learning library for extracting bibliographical information and full texts from technical and scientific documents, in particular from PDF (95%).

Clone the project from github:
	
	git clone https://github.com/kermitt2/grobid.git
Actually Grobid can be used as a service so that we can use multithreaded process, once the service is ready, update the ``anhalytics-harvest/harvest.properties`` file.
###### 3. Nerd (Named Entity Recognition and Disambiguisation)
Nerd annotates the text by detecting named entity recognition, Not open sourced yet (update coming soon ?)
###### 4. ElasticSearch, Elastic
[Elasticsearch](https://github.com/elastic/elasticsearch) is a distributed RESTful search engine built for the cloud, specify the following in the config file (elasticsearch.yml):

    cluster.name: traces # or something else
    index.number_of_shards: 1
    index.number_of_replicas: 0
    cluster.routing.allocation.disk.threshold_enabled: false
    cluster.routing.allocation.disk.watermark.low: 95
    cluster.routing.allocation.disk.watermark.high: 99
    http.jsonp.enable: true

don't forget to update ``anhalytics-frontend/src/main/webapp/search/index.html`` and ``anhalytics-index/index.properties`` with the correct options about ES and nerd.
###### 5. MongoDB
A running instance of [MongoDB](https://www.mongodb.org) is required to store documents, once done add an admin user and update the sub-project property file ``anhalytics-commons/commons.properties``.
###### 6. Apache Tomcat
Tomcat is used as an http server for the deployment of frontend demo.
### Design:
The design of AnHALytics (so far) has five components:

1. Harvest, is the first one to be used to produce the extracted tei along with downloaded metadatas. 
2. Annotate, detects any named entities into identified text(title, abstract, keywords, p ...)
3. Index, we index both the final TEIs and the annotations.
4. Frontend, contain all the demo views (search, author, document, analytics..).
5. Test, to test the interaction of all the processes, a dedicated components is made.

### Compilation:
It's easy, first you can clone a repo with:

    git clone https://github.com/kermitt2/anHALytics
    
Now just compile using maven:

    cd anHALytics
    mvn clean install
### Components:
##### 1. Harvesting

An executable jar file is produced under the directory ``anhalytics-harvest/target``.

The following command displays the help:

> java -jar target/anhalytics-harvest-```<current version>```.one-jar.jar -h

For a large harvesting task, use -Xmx2048m to set the JVM memory to avoid OutOfMemoryException.

###### HarvestAll / HarvestDaily
To start harvesting all the documents of HAL based on [OAI-PMH](http://www.openarchives.org/pmh) v2, use:

> java -Xmx2048m -jar target/anhalytics-harvest-```<current version>```.one-jar.jar -exe harvestAll

Harvesting is done through a reverse chronological order, here is a sample of the OAI-PMH request:
http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&metadataPrefix=xml-tei&from=2015-01-14&until=2015-01-14

To perform an harvesting on a daily basis, use:

> java -Xmx2048m -jar target/anhalytics-harvest-```<current version>```.one-jar.jar -exe harvestDaily

For instance, the process can be configured on a cron table.

###### Grobid processing
Once the document are downloaded TEI extrating threads will run automatically other wise you can run the process with
> java -Xmx2048m -jar target/anhalytics-harvest-```<current version>```.one-jar.jar -exe processGrobid

###### Final TEI building
The final TEI is built and has the following struture

```xml
    <teiCorpus>
        <teiHeader>
            <!-- Harvested metadata , from HAL for instance -->
        </teiHeader>
        <TEI>
            <!-- Grobid extracted data -->
        </TEI>
    </teiCorpus>
```

At least there should be the grobid tei to produce the final TEI, you can do so with :
> java -Xmx2048m -jar target/anhalytics-harvest-```<current version>```.one-jar.jar -exe buildTei

###### About the storage

We use mongoDb along with gridFS component for file support.
Each type of files are stored in a collection apart; hal tei => hal-tei-collection , binaries => binaries-collection,..., 

<!-- documentation of the collections here -->

##### 2. Annotate
Before indexing we need to get standoffs for each document,  and this can be done with annotating using Nerd service.

An executable jar file is produced under the directory ``anhalytics-annotate/target``.

The following command displays the help:
> java -Xmx2048m -jar target/anhalytics-annotate-```<current version>```.one-jar.jar -h

###### Annotation of the HAL collection

The annotation on the HAL collection cab be launch with the command in the main directory of the sub-project ``anhalytics-annotate/``:

>java -Xmx2048m -jar target/anhalytics-annotate-```<current version>```.one-jar.jar -multiThread

(-multiThread option is recommended, it takes time)
###### Storage of annotations

Annotations are preliminary stored in a MongoDB collection before being indexed in ElasticSearch. 

##### 3. Indexing
###### Indexing TEI
To index the final TEIs, in the main directory of the sub-project ``anhalytics-annotate/``:
>java -Xmx2048m -jar target/anhalytics-annotate-```<current version>```.one-jar.jar -index tei

###### Annotation indexing

For indexing the produced annotations in ElasticSearch, in the main directory of the sub-project ``anhalytics-annotate/``:

>java -Xmx2048m -jar target/anhalytics-annotate-```<current version>```.one-jar.jar -index annotation


##### 4. Frontends

A war file is produced under the directory ``anhalytics-frontend/target``, you can use Tomcat for instance to deploy it (make sur the ES and Nerd options are set)

##### 5. Test

## Graph






## License

This code is distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 


