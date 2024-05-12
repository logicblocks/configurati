# configurati

[![Clojars Project](https://img.shields.io/clojars/v/io.logicblocks/configurati.svg)](https://clojars.org/io.logicblocks/configurati)
[![Clojars Downloads](https://img.shields.io/clojars/dt/io.logicblocks/configurati.svg)](https://clojars.org/io.logicblocks/configurati)
[![GitHub Contributors](https://img.shields.io/github/contributors-anon/logicblocks/configurati.svg)](https://github.com/logicblocks/configurati/graphs/contributors)

A Clojure library for managing application configuration.

## Installation

Add the following dependency to your `project.clj` file:

    [io.logicblocks/configurati "0.5.6"]

## Standard Usage

Configurati allows sets of configuration to be defined:

```clojure
(require '[configurati.core :as conf])

(def database-configuration
  (conf/define-configuration
    (conf/with-source (conf/env-source :prefix :my-service))
    (conf/with-parameter :database-host)
    (conf/with-parameter :database-port :type :integer)
    (conf/with-parameter :database-schema :default "default-schema")))
```

This defines a configuration that will look up parameter values from
environment variables, converting `database-port` to an integer and leaving
all other parameters as strings, defaulting `database-schema` to
`"default-schema"`.

Assuming an environment of:

```bash
MY_SERVICE_DATABASE_HOST="db.example.com"
MY_SERVICE_DATABASE_PORT="5000"
```

this configuration resolves to a map as follows:

```clojure
(conf/resolve database-configuration)
; =>
; {:database-host "db.example.com",
;  :database-port 5000
;  :database-schema "default-schema"}
```

### Parameters

Each parameter has a mandatory name, corresponding to the key used to look up
that parameter in the provided configuration sources. Names are always
keywords.

Parameters also have options:

* `:type`: specifies the type of the resulting value. Currently, only `:any`,
  `:string` and `:integer` are supported although the conversions are
  extensible as detailed in the advanced usage section below. Defaults to
  `:any`, performing no conversion of the looked up values.
* `:nilable`: whether the parameter can be `nil`. Either `true` or
  `false`. Defaults to `false`.
* `:validator`: specifies a validator function or keyword referencing a spec
  to validate the parameter against. Validation occurs post-conversion.
* `:default`: the default value to use in the case that no configuration
  source contains this parameter. The default value is converted before being
  returned. Defaults to `nil`.

The `with-parameter` function accepts all of these options:

```clojure
(def database-configuration
  (conf/define-configuration
    (conf/with-parameter :database-host)
    (conf/with-parameter :database-port :type :integer)
    (conf/with-parameter :database-scheme :default "default-schema")
    (conf/with-parameter :database-timeout
      :type :integer :nilable true :validator #(<= % 30000))
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
  (conf/define-configuration
    (conf/with-source (conf/map-source
                        {:database-username "some-username"
                         :database-password "some-password"}))
    ...))
```  

#### env-source

`env-source` looks up parameter values from environment variables.

`env-source` takes an optional prefix prepended to the parameter name before
look up.

```clojure
(def database-configuration
  (conf/define-configuration
    (conf/with-source (conf/env-source :prefix :my-service))
    ...))
```

#### environ-source

`environ-source` uses [environ](https://github.com/weavejester/environ) to look
up
parameter values such that configuration can come from environment variables,
system properties or via the build system (lein or boot).

`environ-source` takes an optional prefix prepended to the parameter name before
look up.

```clojure
(def database-configuration
  (conf/define-configuration
    (conf/with-source (conf/env-source :prefix :my-service))
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
  (conf/define-configuration
    (conf/with-source (conf/yaml-file-source "path/to/config.yaml"
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
  (conf/define-configuration
    (conf/with-source
      (conf/multi-source
        (conf/env-source)
        (conf/yaml-file-source "path/to/config.yaml")))
    ...))
```

Note, if multiple sources are provided to `define-configuration`, a
`multi-source` is automatically created in the background passing the sources
in the same order as they are provided.

### Source Middleware

Sources support middleware allowing parameter keys to be transformed before
they are passed to the source and parameter values to be transformed before
they are returned.

Since middleware apply to sources, they take effect before parameter value
conversion takes place.

Configurati includes a number of parameter value transforming middlewares
as detailed in the subsequent sections.

#### json-parsing-middleware

`json-parsing-middleware` parses parameter values as JSON. It allows
configuration of:

* which parameters to target;
* how to perform the parsing.

By default, `json-parsing-middleware` parses all parameter values retrieved
from the source, converting keys to keywords but not changing their casing:

```clojure
(require '[configurati.middleware :as conf-mdlw])

(def issuer-configuration
  (conf/define-configuration
    (conf/with-source
      (conf/map-source
        {:issuer-1 "{\"url\": \"https://issuer-1.example.com\"}"
         :issuer-2 "{\"url\": \"https://issuer-2.example.com\"}"})
      (conf/with-middleware (conf-mdlw/json-parsing-middleware)))
    (conf/with-parameter :issuer-1)
    (conf/with-parameter :issuer-2)))
```

which resolves to:

```clojure
(conf/resolve issuer-configuration)
; =>
; {:issuer-1 {:url "https://issuer-1.example.com"}
;  :issuer-2 {:url "https://issuer-2.example.com"}}
```

To convert only certain parameters, pass their parameter keys in the `:only`
option:

```clojure
(def issuer-configuration
  (conf/define-configuration
    (conf/with-source
      (conf/map-source
        {:issuer  "{\"url\": \"https://issuer-1.example.com\"}"
         :timeout 10000})
      (conf/with-middleware
        (conf-mdlw/json-parsing-middleware
          :only [:issuer])))
    (conf/with-parameter :issuer)
    (conf/with-parameter :timeout)))
```

which resolves to:

```clojure
(resolve issuer-configuration)
; =>
; {:issuer {:url "https://issuer-1.example.com"}
;  :timeout 10000}
```

To configure parsing, there are two options available:

* `:key-fn` replaces the function used to convert keys in the resulting map;
* `:parse-fn` replaces the entire JSON parsing function.

For example, to kebab case keys:

```clojure
(require '[camel-snake-kebab.core :as csk])

(def issuer-configuration
  (conf/define-configuration
    (conf/with-source
      (conf/map-source
        {:authentication "{\"issuerUrl\": \"https://issuer-1.example.com\"}"})
      (conf/with-middleware
        (conf-mdlw/json-parsing-middleware
          :key-fn csk/kebab-case-keyword)))
    (conf/with-parameter :authentication)))
```

which resolves to:

```clojure
(conf/resolve issuer-configuration)
; =>
; {:authentication {:issuer-url "https://issuer-1.example.com"}}
```

#### separator-parsing-middleware

`separator-parsing-middleware` splits parameter values on a separator. It allows
configuration of:

* which parameters to target;
* how to perform the parsing.

By default, `separator-parsing-middleware` parses all parameter values retrieved
from the source, splitting on comma:

```clojure
(def supplier-configuration
  (conf/define-configuration
    (conf/with-source
      (conf/map-source
        {:countries  "USA,GBR,DEU"
         :currencies "USD,GBP,EUR"})
      (conf/with-middleware (conf-mdlw/separator-parsing-middleware)))
    (conf/with-parameter :countries)
    (conf/with-parameter :currencies)))
```

which resolves to:

```clojure
(conf/resolve issuer-configuration)
; =>
; {:countries ["USA" "GBR" "DEU"]
;  :currencies ["USD" "GBP" "EUR"]}
```

To convert only certain parameters, pass their parameter keys in the `:only`
option:

```clojure
(def supplier-configuration
  (conf/define-configuration
    (conf/with-source
      (conf/map-source
        {:countries "USA,GBR,DEU"
         :name      "Supplier, Ltd."})
      (conf/with-middleware (conf-mdlw/separator-parsing-middleware
                              :only [:countries])))
    (conf/with-parameter :countries)
    (conf/with-parameter :currencies)))
```

which resolves to:

```clojure
(conf/resolve issuer-configuration)
; =>
; {:countries ["USA" "GBR" "DEU"]
;  :name "Supplier, Ltd."}
```

To configure parsing, there are three options available:

* `:separator` defines the character on which to split, defaulting to `","`;
* `:trim` indicates whether to trim the split values, defaulting to
  `true`;
* `:parse-fn` which replaces the entire parsing function, ignoring the above
  two options.

For example, to split on pipe characters:

```clojure
(require '[camel-snake-kebab.core :as csk])

(def supplier-configuration
  (conf/define-configuration
    (conf/with-source
      (conf/map-source
        {:countries "USA|GBR|DEU"})
      (conf/with-middleware
        (conf-mdlw/separator-parsing-middleware
          :separator "|")))
    (conf/with-parameter :countries)))
```

which resolves to:

```clojure
(conf/resolve supplier-configuration)
; =>
; {:countries ["USA" "GBR" "DEU"]}
```

### Key Functions

You may wish to refer to configuration parameters differently in code compared
to how they are specified in the configuration sources. Key functions enable
this.

When a configuration is defined, one or more key functions can be provided
allowing keys to be transformed during configuration resolution. A key
function receives each key and returns its replacement. A number of key
functions are provided in `configurati.key-fns`.

```clojure
(require '[configurati.key-fns :as conf-kf])

(def api-configuration
  (conf/define-configuration
    (conf/with-source
      (conf/map-source {:api-username "some-username"
                        :api-password "some-password"
                        :api-port     "5000"}))
    (conf/with-parameter :api-username)
    (conf/with-parameter :api-password)
    (conf/with-parameter :api-port :type :integer)
    (conf/with-key-fn (conf-kf/remove-prefix :api))
    (conf/with-key-fn (conf-kf/add-prefix :service))))

(conf/resolve api-configuration)
; =>
; {:service-username "some-username"
;  :service-password "some-password"
;  :service-port     5000}
```

### Transformations

In some cases, you may need to change the shape of a configuration map or add,
modify or remove elements from it. Transformations enable this.

When a configuration is defined, one or more transformations can be provided
allowing the configuration map to be transformed during configuration
resolution. A transformation function receives the resolved configuration map
and returns it after applying the transformation.

```clojure
(def api-configuration
  (conf/define-configuration
    (conf/with-source
      (conf/map-source {:username "some-username"
                        :password "some-password"
                        :port     "5000"}))
    (conf/with-parameter :username)
    (conf/with-parameter :password)
    (conf/with-parameter :port :type :integer)
    (conf/with-transformation (fn [m] {:api m}))))

(conf/resolve api-configuration)
; =>
; {:api
;  {:username "some-username"
;   :password "some-password"
;   :port     5000}}
```

### Specifications

A set of parameters makes up a configuration specification. Configuration
specifications can be created separately from defining configuration:

```clojure
(def database-configuration-specification
  (conf/define-configuration-specification
    (conf/with-parameter :database-host)
    (conf/with-parameter :database-port :type :integer)
    (conf/with-parameter :database-scheme :default "default-schema")))

...

(def database-configuration
  (conf/define-configuration
    (conf/with-specification
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
  (conf/define-configuration-specification
    (conf/with-parameter :database-host)
    (conf/with-parameter :database-port :type :integer)
    (conf/with-parameter :database-scheme :default "default-schema")))

(def service-configuration-specification
  (conf/define-configuration-specification
    (conf/with-parameter :service-host)
    (conf/with-parameter :service-port :type :integer)
    (conf/with-parameter :service-token)))

...

(def database-configuration
  (conf/define-configuration
    (conf/with-specification
      database-configuration-specification)
    (conf/with-specification
      service-configuration-specification)
    (conf/with-parameter :other-parameter)
    ...))
```

is the same as:

```clojure
(def database-configuration
  (conf/define-configuration
    (conf/with-parameter :database-host)
    (conf/with-parameter :database-port :type :integer)
    (conf/with-parameter :database-scheme :default "default-schema")
    (conf/with-parameter :service-host)
    (conf/with-parameter :service-port :type :integer)
    (conf/with-parameter :service-token)
    (conf/with-parameter :other-parameter)
    ...))
```

A configuration specification optionally takes one or more key functions
similar to those described above:

```clojure
(require '[configurati.key-fns :as conf-kf])

(def database-configuration-specification
  (conf/define-configuration-specification
    (conf/with-key-fn (conf-kf/remove-prefix :api))
    ...))
```

When configuration specifications are merged as part of a definition,
their key functions are composed together in the reverse order the
specifications are provided with any key functions on the definition itself
applying last:

```clojure
(def specification-1
  (conf/define-configuration-specification
    (conf/with-key-fn (fn [key] (str "s1-" (name key))))))

(def specification-2
  (conf/define-configuration-specification
    (conf/with-key-fn (fn [key] (str "s2-" (name key))))))

(def configuration
  (conf/define-configuration
    (conf/with-specification specification-1)
    (conf/with-specification specification-2)
    (conf/with-parameter :param)
    (conf/with-key-fn (fn [key] (str "def-" (name key))))
    (conf/with-key-fn (fn [key] (keyword key)))
    (conf/with-source (conf/map-source {:param "val"}))))

(conf/resolve configuration)
; =>
; {:def-s2-s1-param "val"}
```

## Advanced Usage

### Custom converters

To add a parameter type, implement the `convert-to` multimethod in
`configurati.conversions`:

```clojure
(require '[configurati.conversions :as conf-conv])

(defmethod conf-conv/convert-to :boolean [_ value]
  (if (#{"true" true} value) true false))

(def configuration
  (conf/define-configuration
    (conf/with-source (conf/map-source {:encrypted? "true"}))
    (conf/with-parameter :encrypted? :type :boolean)))

(conf/resolve configuration)
; =>
; {:encrypted? true}
```

### Custom middleware

To create a custom middleware, create a function that returns a function as
follows:

```clojure
(defn custom-middleware []
  (fn [source parameter-name]
    ...))
```

Within the body of the returned function, call `source` with `parameter-name`
or a derivative, transform and return the response.

## License

Copyright &copy; 2024 LogicBlocks Maintainers

Distributed under the terms of the
[MIT License](http://opensource.org/licenses/MIT).
