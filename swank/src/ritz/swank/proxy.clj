(ns ritz.swank.proxy
  "Swank proxy server.  Sits between slime and the target swank process"
  (:require
   [clojure.pprint :as pprint]
   [ritz.debugger.executor :as executor]
   [ritz.jpda.jdi :as jdi]
   [ritz.jpda.jdi-clj :as jdi-clj]
   [ritz.jpda.jdi-vm :as jdi-vm]
   [ritz.logging :as logging]
   [ritz.repl-utils.compile :as compile]
   [ritz.repl-utils.io :as io]
   [ritz.swank :as swank]
   [ritz.swank.commands :as commands]
   [ritz.swank.core :as core]
   [ritz.swank.debug :as debug]
   [ritz.swank.hooks :as hooks]
   [ritz.swank.messages :as messages]
   [ritz.swank.rpc-server :as rpc-server]
   ;; order is important for these to overide functions defined on local
   ;; vm, vs functions defined for jpda/jdi connection
   ritz.swank.commands.inspector
   ritz.swank.commands.debugger
   ritz.swank.commands.contrib.ritz)
  (:use
   [ritz.swank.connections :only [add-connection]])
  (:import
   com.sun.jdi.VirtualMachine))

(defn forward-commands
  "Alter eval-for-emacs to forward unrecognised commands to proxied connection."
  []
  ;; (alter-var-root
  ;;  #'swank/command-not-found
  ;;  (fn [_ x] x)
  ;;  debug/forward-command)
  (alter-var-root
   #'swank/forward-rpc
   (fn [_] debug/forward-rpc)))

(def swank-pipeline
  (debug/execute-if-inspect-frame-var
   (debug/execute-inspect-if-inspector-active
    (debug/execute-unless-inspect
     (debug/execute-peek
      (debug/forward-command
       core/command-not-found))))))

(defn serve-connection
  "Serve connection for proxy rpc functions"
  []
  (logging/trace "proxy/serve-connection")
  (.setName (Thread/currentThread) "REPL Proxy")
  (fn proxy-connection-handler
    [io-connection options]
    (logging/trace "proxy/proxy-connection-handler")
    (forward-commands)
    (let [options (->
                   options
                   (dissoc :announce)
                   (merge {:port 0 :join true :server-ns 'ritz.repl}))
          vm-context (debug/launch-vm-with-swank options)
          options (assoc options :vm-context vm-context)]
      (logging/trace "proxy/connection-handler: runtime set")
      (logging/trace "proxy/connection-handler: thread-groups")
      (logging/trace (with-out-str
                       (pprint/pprint (jdi/thread-groups (:vm vm-context)))))

      (logging/trace "proxy/connection-handler: resume vm")
      (.resume ^VirtualMachine (:vm vm-context))

      (logging/trace "proxy/connection-handler: thread-groups")
      (logging/trace
       (with-out-str (pprint/pprint (jdi/thread-groups (:vm vm-context)))))

      (let [port (debug/remote-swank-port vm-context)]
        (logging/trace "proxy/connection-handler proxied server on %s" port)
        (if (= port (:port options))
          (do
            (logging/trace "invalid port")
            ((:close-connection io-connection) io-connection))
          (let [proxied-connection (debug/create-connection
                                    (assoc options :port port))
                _ (logging/trace
                   "proxy/connection-handler connected to proxied")
                [connection future] (rpc-server/serve-connection
                                     io-connection
                                     (merge
                                      options
                                      {:proxy-to proxied-connection
                                       :swank-handler swank-pipeline}))]
            (logging/trace "proxy/connection-handler running")
            (executor/execute-loop
             (partial debug/forward-reply connection) :name "Reply pump")
            (logging/trace "proxy/connection-handler reply-pump running")
            (hooks/run core/new-connection-hook connection)
            (logging/trace "proxy/connection-handler new-connection-hook ran")
            (add-connection connection proxied-connection)
            (logging/trace "proxy/connection-handler connection added")
            (debug/add-exception-event-request vm-context)
            (logging/trace
             "proxy/connection-handler exeception events requested")))))))
