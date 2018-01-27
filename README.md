# configurati

[![Clojars Project](https://img.shields.io/clojars/v/configurati.svg)](https://clojars.org/configurati)

Clojure library for managing application configuration.

## Installation

Add the following dependency to your `project.clj` file:

    [configurati "0.1.0"]

## Usage

Configurati allows sets of configuration to be defined:

```clojure
(require '[configurati.core :refer :all])

(def database-configuration
  (define-configuration
    (with-source (env-source :prefix :my-service))
    (with-parameter :database-host)
    (with-parameter :database-port :as :integer)
    (with-parameter :database-schema :default "default-schema")))
```

This defines a configuration that will look up parameter values in the 
environment using `environ`, converting `database-port` to an integer and 
leaving all other parameters as strings, defaulting `database-schema` to
`"default-schema"`.  

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

## License

Copyright © 2017 Toby Clemson

Distributed under the MIT license.
