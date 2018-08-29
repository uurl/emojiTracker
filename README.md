# Seguimiento de emojis casi en tiempo real

## Resumen
Este repositorio contiene un ejemplo práctico de cómo crear una aplicación moderna centrada en datos para rastrear la aparición de emojis en los tweets públicos en casi tiempo real. Utiliza principalmente las siguientes tecnologías:

- Ingestión de datos: [Apache Kafka Connect](https://kafka.apache.org/documentation/#connect)
- Persistencia: [Apacha Kafka](https://kafka.apache.org)
- Procesamiento de streams: [Apacha Kafka Streams](https://kafka.apache.org/documentation/streams/)
- Capa de integración RPC y WebAPI reactiva: [Spring Boot 2.0](https://projects.spring.io/spring-boot/)

## Ejemplo de uso:

Los siguientes párrafos dan una explicación detallada paso a paso para configurar y ejecutar la aplicación en local.

#### 1. Iniciar el entorno Kafka:
La aplicación necesita un entorno Kafka funcional, idealmente en local. Si te gustan los contenedores y sabes cómo usar Docker, puedes usar las imágenes Docker preconstruidas de Apache Kafka (por ejemplo, las proporcionadas por [Confluent](https://hub.docker.com/r/confluentinc/)). Por simplicidad, es mejor iniciar todos los procesos de Kafka en la [CLI](https://docs.confluent.io/current/cli/index.html) que viene con la versión de código abierto de la [Confluent's Platform](https://www.confluent.io/download/)

Muévete a la carpeta de instalación (por ejemplo, /usr/local/confluent-4.1.1/) y ejecuta

```bash
bin/confluent start
```

Esto debería iniciar los procesos relacionados con Kafka, concretamente: _zookeeper, kafka, schema-registry, kafka-rest y connect_ y puede tomar unos minutos a cada uno para llegar al estado **[UP]**:

```bash
Starting zookeeper
zookeeper is [UP]
Starting kafka
kafka is [UP]
Starting schema-registry
schema-registry is [UP]
Starting kafka-rest
kafka-rest is [UP]
Starting connect
connect is [UP]
Starting ksql-server
ksql-server is [UP]
```

_Si tienes algún problema corriendo la Confluent Platform, lee la documentación (cuando todo falle hay que leer el manual)_

#### 2. Crear un tópico de Kafka para almacenar tweets:

Antes de poder ingerir tweets en vivo, es necesario crear un tópico de Kafka. Esto se hace con las herramientas de línea de comandos que vienen con Kafka. El siguiente comando crea un tópico llamado **live-tweets** con _4 particiones_ y un _factor de replicación de 1_

```bash
bin/kafka-topics --zookeeper localhost:2181 --topic live-tweets --create --replication-factor 1 --partitions 4
```

#### 3. Ejecutar el conector de Twitter para obtener los tweets públicos en vivo:

Existe una [gran cantidad de conectores Kafka](https://www.confluent.io/product/connectors/) disponibles para leer datos de distintas fuentes y escribir datos en diferentes receptores. Esta aplicación usa el [conector de Twitter](https://github.com/jcustenborder/kafka-connect-twitter) de la comunidad. Para que este conector esté disponible en local, copia el directorio que contenga los artefactos de compilación o una [versión preconstruida](https://github.com/jcustenborder/kafka-connect-twitter/releases/tag/0.2.26) junto con todas sus dependencias al directorio de instalación de la Confluent Platform. Después de descomprimir el artefacto del conector, copia el contenido

desde

```bash
kafka-connect-twitter-0.2.26/usr/share/kafka-connect/kafka-connect-twitter
```
hasta

```bash
/usr/local/confluent-4.1.1/share/java/
```

Para que kafka connect pueda detectar la disponibilidad de este conector recién instalado, hay que reiniciar el proceso _connect_ con la CLI ejecutando:

```bash
bin/confluent stop connect
```

seguido de:

```bash
bin/confluent start connect
```

Ahora el conector de Twitter está listo para usarse. Se puede configurar y administrar fácilmente mediante la [API REST de Kafka connect](https://docs.confluent.io/current/connect/restapi.html). Para comprobar si el conector está disponible hay que enviar la siguiente solicitud GET, por ejemplo usando CURL, Postman u otra herramienta:

```bash
curl http://localhost:8083/connector-plugins
```

Esto debería dar como resultado un arreglo JSON con todos los _conectores_ de Kafka connect actualmente disponibles para su instalación. En algún lugar entre las líneas está el conector de Twitter:

```json
[
  ...
  {
    "class": "com.github.jcustenborder.kafka.connect.twitter.TwitterSourceConnector",
    "type": "source",
    "version": "0.2.26"
  }
  ...
]
```

Como son demasiados tweets, podemos ejecutar el conector para rastrear un subconjunto de tweets en vivo relacionados con algunas palabras clave (modificando la entrada **filter.keywords**) en función de la siguiente configuración de JSON. Simplemente hay que insertar los tokens /secrets_OAuth que obtienen al crear una aplicación de Twitter con una cuenta. Esto es lo primero que debes crear para obtener acceso a la API de Twitter. Envía la configuración JSON como una solicitud POST usando CURL o Postman:

```json
{ "name": "twitter_source_01",
  "config": {
    "connector.class": "com.github.jcustenborder.kafka.connect.twitter.TwitterSourceConnector",
    "twitter.oauth.accessToken": "...",
    "twitter.oauth.consumerSecret": "...",
    "twitter.oauth.consumerKey": "...",
    "twitter.oauth.accessTokenSecret": "...",
	"kafka.status.topic": "live-tweets",
	"process.deletes": false,
	"value.converter": "org.apache.kafka.connect.json.JsonConverter",
	"value.converter.schemas.enable": false, 
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": false,
    "filter.keywords": "money,bitcoin,cryptocurrency,blockchain,ethereum,shitcoin,bitcoinbubble"
    }
}
```

Esto debería dar como resultado una respuesta _HTTP status 201 created_

#### 4. Comprobar la ingestión de datos
Mediante la línea de comandos de Kafka es fácil verificar si los tweets están fluyendo en el tópico. Ejecutando lo siguiente en el directorio de la Confluent Platform

```bash
bin/kafka-console-consumer --bootstrap-server localhost:9092 --topic live-tweets --from-beginning
```

debe consumir todos los tweets en el tópico **live-tweets** y escribirlos directamente en _stdout_ a medida que entran. La estructura JSON de los tweets basados ​​en el conector es bastante detallada. La aplicación de ejemplo deserializa solo los siguientes 4 campos mientras que solo hace uso del campo de texto para extraer cualquier emoji durante el procesamiento de la secuencia:

```json
{
    "CreatedAt": 1515751050000,
    "Id": 951755003055820800,
    "Text": "Google details how it protected services like Gmail from Spectre https://t.co/jyuEixDaQq #metabloks",
    "Lang": "en"
}
```

#### 5. Ejecutar Spring Boot 2.0 emojitracker
Todo está configurado para iniciar la aplicación de procesamiento de flujo. Simplemente hay que correr el proyecto maven ejecutando:

```bash
mvn clean package
```

luego ejecutar la aplicación desde la línea de comando usando:

```bash
java -jar -Dserver.port=8881 -Dkstreams.tweetsTopic=live-tweets target/emojiTracker-1.0-SNAPSHOT.jar
```

#### 6. Consultar interactivamente el estado de la aplicación kstreams
Después de que la aplicación se haya iniciado correctamente, puedes realizar llamadas REST para consultar el recuento de emojis:

##### Para consultar todos los emojis rastreados hasta el momento:

```bash
curl -X GET http://localhost:8881/interactive/queries/emojis/
```

El resultado no está en un orden particular y la salida debe parecerse a lo siguiente:

```json
[
    ...
    {
        "emoji": "🐾",
        "count": 144
    },
    {
        "emoji": "👇",
        "count": 256
    },
    {
        "emoji": "👉",
        "count": 140
    },
    {
        "emoji": "💀",
        "count": 800
    },
    {
        "emoji": "💋",
        "count": 30
    },
    {
        "emoji": "💖",
        "count": 0
    },
    {
        "emoji": "💥",
        "count": 90
    },
    ...
]
```

_NOTA: Obviamente los números obtenidos varían_

##### Consultar un emoji específico hasta el momento:
Al usar CURL, es necesario especificar el emoji por medio de su código de escape URL. Por tanto, es más conveniente consultar con Postman o un navegador, ya que esto permite colocar directamente los emojis en la URL.

http://localhost:8881/interactive/queries/emojis/😂

```bash
curl -X GET http://localhost:8881/interactive/queries/emojis/%F0%9F%98%82
```

{
    "emoji": "😂",
    "count": 1000
}

_NOTA: Obviamente los números obtenidos varían_

##### Para consultar los N emojis más usados hasta el momento:

```bash
curl -X GET http://localhost:8881/interactive/queries/emojis/stats/topN
```

```json
[
    {
        "emoji": "😂",
        "count": 256
    },
    {
        "emoji": "😭",
        "count": 100
    },
    {
        "emoji": "➡",
        "count": 81
    },
    {
        "emoji": "✨",
        "count": 80
    },
    {
        "emoji": "⚡",
        "count": 79
    },
    {
        "emoji": "🌎",
        "count": 77
    },
    {
        "emoji": "👉",
        "count": 64
    },
    {
        "emoji": "💀",
        "count": 29
    },
    {
        "emoji": "❤",
        "count": 21
    },
    {
        "emoji": "🔥",
        "count": 17
    },
    ...
]
```
_NOTA: Obviamente los números obtenidos varían_


##### Cambia la secuencia de actualización de conteo de emojis

Las aplicaciones cliente pueden suscribirse a un flujo de cambio reactivo de actualizaciones de conteo emoji mientras las aplicaciones kstream procesan datos nuevos. Esto da como resultado que se transmita contenido continuamente a los clientes para consumirlos desde JavaScript y construir una tabla HTML al vuelo.

```bash
curl -X GET http://localhost:8881/interactive/queries/emojis/updates/notify
```

```json

...

data: {"emoji": "❤","count": 67}

data: {"emoji": "💀","count": 200}

data: {"emoji": "🌎","count": 18}

data: {"emoji": "🔥","count": 98}

data: {"emoji": "👇","count": 56}

...

```

#### 7. Opcional: ejecutar varias instancias de la aplicación kstreams

Para ejecutar varias instancias y experimentar con la escalabilidad y tolerancia a fallos de kstream, simplemente inicia la aplicación varias veces. **Ten cuidado de usar diferentes configuraciones _server.port_ y _live.demo.instance.id_ para cada instancia adicional**

Por ejemplo, para iniciar una segunda instancia:

```bash
java -jar -Dserver.port=8882 -Dlive.demo.instance.id=2 -Dkstreams.tweetsTopic=live-tweets target/emojiTracker-1.0-SNAPSHOT.jar
```

Ahora puedes consultar cualquiera de las dos instancias para obtener los resultados del recuento de emojis.
