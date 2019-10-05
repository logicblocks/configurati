# configurati

[![Clojars Project](https://img.shields.io/clojars/v/io.logicblocks/configurati.svg)](https://clojars.org/io.logicblocks/configurati)

Clojure library for managing application configuration.

## Installation

Add the following dependency to your `project.clj` file:

    [logicblocks/configurati "0.5.2"]

## Standard Usage

Configurati allows sets of configuration to be defined:

```clojure
(require '[configurati.core :refer :all])

(def database-configuration
  (define-configuration
    (with-source (env-source :prefix :my-service))
    (with-parameter :database-host)
    (with-parameter :database-port :type :integer)
    (with-parameter :database-schema :default "default-schema")))
```

This defines a configuration that will look up parameter values in the 
environment using [environ](https://github.com/weavejester/environ), converting 
`database-port` to an integer and leaving all other parameters as strings, 
defaulting `database-schema` to `"default-schema"`.  

Assuming an environment of:

```bash
MY_SERVICE_DATABASE_HOST="db.example.com"
MY_SERVICE_DATABASE_PORT="5000"
```
this configuration resolves to a map as follows:

```clojure
(resolve database-configuration)
=>
{:database-host "db.example.com",
 :database-port 5000
 :database-schema "default-schema"}
```

### Parameters

Each parameter has a mandatory name, corresponding to the key used to lookup 
that parameter in the provided configuration sources. Names are always 
keywords.

Parameters also have options:
 * `:type`: specifies the type of the resulting value. Currently, only `:string`
   and `:integer` are supported although the conversions are extensible as
   detailed in the advanced usage section below. Defaults to `:string`.
 * `:nilable`: whether or not the parameter can be `nil`. Either `true` or 
   `false`. Defaults to `false`.
 * `:default`: the default value to use in the case that no configuration 
   source contains this parameter. The default value is converted before being
   returned. Defaults to `nil`.

The `with-parameter` function accepts all of these options:

```clojure
(def database-configuration
  (define-configuration
    (with-parameter :database-host)
    (with-parameter :database-port :type :integer)
    (with-parameter :database-scheme :default "default-schema")
    (with-parameter :database-timeout :nilable true)
    ...))
```

### Sources

Each parameter is looked up in a configuration source. A configuration source
is anything that implements `clojure.lang.ILookup`.

There are a number of configuration sources included with Configurati as 
detailed in the subsequent sections.

#### map-source

`map-source` uses an in memory map to look up parameter values:

```clojure
(def database-configuration
  (define-configuration
    (with-source (map-source 
                   {:database-username "some-username"
                    :database-password "some-password"}))
    ...))
```  

#### env-source

`env-source` uses [environ](https://github.com/weavejester/environ) to look up
parameter values such that configuration can come from environment variables,
system properties or via the build system (lein or boot).

`env-source` takes an optional prefix prepended to the parameter name before
look up.

```clojure
(def database-configuration
  (define-configuration
    (with-source (env-source :prefix :my-service))
    ...))
```

#### yaml-file-source

`yaml-file-source` loads configuration from a YAML file at the provided path.
Under the covers, `yaml-file-source` uses `slurp` so everything it supports,
e.g., URIs, are also supported.

`yaml-file-source` takes an optional prefix prepended to the parameter name
before look up.

```clojure
(def database-configuration
  (define-configuration
    (with-source (yaml-file-source "path/to/config.yaml" 
                   :prefix :my-service))
    ...))
```

The file at `path/to/config.yaml` would look something like:

```yaml
my_service_database_username: "some-username"
my_service_database_password: "some-password"
```

#### multi-source

Sometimes it is useful to look up configuration from a number of different
sources that form a configuration hierarchy. 

`multi-source` takes a number of sources and looks up parameter values in the
order the sources are specified at construction.

```clojure
(def database-configuration
  (define-configuration
    (with-source
      (multi-source 
        (env-source)
        (yaml-file-source "path/to/config.yaml")))
    ...))
```

Note, if multiple sources are provided to `define-configuration`, a 
`multi-source` is automatically created in the background passing the sources
in the same order as they are provided.

### Key Functions

You may wish to refer to configuration parameters differently in code compared
to how they are specified in the configuration sources. Key functions enable 
this.

When a configuration is defined, one or more key functions can be provided
allowing keys to be transformed during configuration resolution. A key 
function receives each key and returns its replacement. A number of key 
functions are provided in `configurati.key-fns`. 

```clojure
(require '[configurati.key-fns :refer [add-prefix remove-prefix]])

(def api-configuration
  (define-configuration
    (with-source
      (map-source {:api-username "some-username"
                   :api-password "some-password"
                   :api-port     "5000"}))
    (with-parameter :api-username)
    (with-parameter :api-password)
    (with-parameter :api-port :type :integer)
    (with-key-fn (remove-prefix :api))
    (with-key-fn (add-prefix :service))))

(resolve api-configuration)
=>
{:service-username "some-username"
 :service-password "some-password"
 :service-port     5000}
```

### Specifications

A set of parameters makes up a configuration specification. Configuration
specifications can be created separately from defining configuration:

```clojure
(def database-configuration-specification
  (define-configuration-specification
    (with-parameter :database-host)
    (with-parameter :database-port :type :integer)
    (with-parameter :database-scheme :default "default-schema")))

...

(def database-configuration
  (define-configuration
    (with-specification
      database-configuration-specification)
    ...))
```

This is useful if different configuration sources need to be used at different
times for the same configuration specification.

`define-configuration` supports multiple specifications whose parameters are
merged together to form one specification. Additional parameters can also be
specified:

```clojure
(def database-configuration-specification
  (define-configuration-specification
    (with-parameter :database-host)
    (with-parameter :database-port :type :integer)
    (with-parameter :database-scheme :default "default-schema")))

(def service-configuration-specification
  (define-configuration-specification
    (with-parameter :service-host)
    (with-parameter :service-port :type :integer)
    (with-parameter :service-token)))

...

(def database-configuration
  (define-configuration
    (with-specification
      database-configuration-specification)
    (with-specification
      service-configuration-specification)
    (with-parameter :other-parameter)
    ...))
```

is the same as:

```clojure
(def database-configuration
  (define-configuration
    (with-parameter :database-host)
    (with-parameter :database-port :type :integer)
    (with-parameter :database-scheme :default "default-schema")
    (with-parameter :service-host)
    (with-parameter :service-port :type :integer)
    (with-parameter :service-token)
    (with-parameter :other-parameter)
    ...))
```

A configuration specification optionally takes one or more key functions 
similar to those described above:

```clojure
(require '[configurati.key-fns :refer [remove-prefix]])

(def database-configuration-specification
  (define-configuration-specification
    (with-key-fn (remove-prefix :api))
    ...))
```

When configuration specifications are merged as part of a definition, 
their key functions are composed together in the order the specifications
are provided with any key functions on the definition itself applying last:

```clojure
(def specification-1
  (define-configuration-specification
    (with-key-fn (fn [key] (str "s1-" (name key))))))
    
(def specification-2
  (define-configuration-specification
    (with-key-fn (fn [key] (str "s2-" (name key))))))
    
(def configuration
  (define-configuration
    (with-specification specification-1)
    (with-specification specification-2)
    (with-parameter :param)
    (with-key-fn (fn [key] (keyword key)))
    (with-key-fn (fn [key] (str "def-" (name key))))
    (with-source (map-source {:param "val"}))))

(resolve configuration)
=>
{:def-s1-s2-param "val"}
```

## Advanced Usage

### Custom converters

To add a parameter type, implement the `convert-to` multimethod in
`configurati.conversions`: 

```clojure
(require '[configurati.conversions :refer [convert-to]])

(defmethod convert-to :boolean [_ value]
  (if (#{"true" true} value) true false))

(def configuration
  (define-configuration
    (with-source (map-source {:encrypted? "true"}))
    (with-parameter :encrypted? :type :boolean)))

(resolve configuration)
=>
{:encrypted? true}
```

## License

Copyright © 2018 Toby Clemson

Distributed under the MIT license.
