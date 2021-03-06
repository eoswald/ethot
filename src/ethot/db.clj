(ns ethot.db
  (:require [config.core :refer [env]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:gen-class))

(def db-name "ethot")
(def db-host (:mysql-host env))
(def db-user (:mysql-user env))
(def db-password (:mysql-pass env))

(def ds (jdbc/get-datasource
         {:dbtype "mysql"
          :dbname db-name
          :host db-host
          :user db-user
          :password db-password}))

(defn create-veto-lobby
  "Creates a veto lobby in the database."
  [match-id tournament-id ebot-match-id team1 team2 discord-channel-id]
  (let [team1-id (get team1 "id")
        team2-id (get team2 "id")
        next-ban (first (shuffle (list team1 team2)))
        next-ban-id (get next-ban "id")]
    (jdbc/execute-one! ds ["insert into veto (match_id, tournament_id, ebot_match_id, team1_id, team2_id, discord_channel_id, next_ban_id)
                            values (?, ?, ?, ?, ?, ?, ?)"
                           match-id tournament-id ebot-match-id team1-id team2-id discord-channel-id next-ban-id]
                       {:builder-fn rs/as-unqualified-lower-maps})
    next-ban))

(defn get-veto-lobby
  "Gets a veto lobby from the database from it's discord channel id."
  [discord-channel-id]
  (jdbc/execute-one! ds ["select * from veto
                          where discord_channel_id = ?
                          and active = 1" discord-channel-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(defn ban-map
  "Bans a map in a veto lobby."
  [match-id map-name next-ban-id]
  (jdbc/execute-one! ds [(str "update veto "
                              "set " map-name " = 1, "
                              "next_ban_id = " next-ban-id " "
                              "where match_id = " match-id)]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(defn end-veto
  "Marks a veto lobby inactive."
  [match-id]
  (jdbc/execute-one! ds ["update veto
                          set active = 0
                          where match_id = ?" match-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(defn delay-match
  "Adds a match to the delays table."
  [match-id]
  (jdbc/execute-one! ds ["insert into delays (match_id)
                          values (?)" match-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(defn match-delayed?
  "Checks if a match is delayed."
  [match-id]
  (not= (:c (jdbc/execute-one! ds ["select count(*) as c
                                    from delays
                                    where match_id = ?" match-id]
                               {:builder-fn rs/as-unqualified-lower-maps}))
        0))

(defn resume-match
  "Removes a match from the delays table."
  [match-id]
  (jdbc/execute-one! ds ["delete from delays
                          where match_id = ?" match-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(defn add-unreported
  "adds a match to the reports table as unreported"
  [match-id]
  (jdbc/execute-one! ds ["insert into reports (ebot_match_id, report_status)
                          values (?, ?)" match-id 0]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(defn set-unreported
  "Mark the match-id as unreported in the reports table"
  [match-id]
  (jdbc/execute-one! ds ["update reports
                          set report_status = 0
                          where ebot_match_id = ?" match-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(defn set-report-timer
  "Mark the match-id as timer started in the reports table"
  [match-id]
  (jdbc/execute-one! ds ["update reports
                          set report_status = 1
                          where ebot_match_id = ?" match-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(defn set-reported
  "Mark the match-id as reported in the reports table"
  [match-id]
  (jdbc/execute-one! ds ["update reports
                          set report_status = 2
                          where ebot_match_id = ?" match-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(defn set-exported
  "Mark the match-id as exported in the reports table"
  [match-id]
  (jdbc/execute-one! ds ["update reports
                          set report_status = 3
                          where ebot_match_id = ?" match-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(defn report-status-value
  [match-id]
  (:report_status
   (jdbc/execute-one! ds ["select *
                           from reports
                           where ebot_match_id = ?" match-id]
                      {:builder-fn rs/as-unqualified-lower-maps})))

(defn unreported?
  "See if the match-id is set as unreported in the reports table"
  [match-id]
  (= (report-status-value match-id) 0))

(defn report-timer-started?
  "See if the match-id is marked as timer-started in the reports table"
  [match-id]
  (> (report-status-value match-id) 0))

(defn in-timer?
  "See if the match-id is marked as timer-started in the reports table"
  [match-id]
  (= (report-status-value match-id) 1))

(defn reported?
  "See if the match-id is marked as reported in the reports table"
  [match-id]
  (= (report-status-value match-id) 2))

(defn in-reports-table?
  "See if the match-id is in the reports table"
  [match-id]
  (not=
   (:c
    (jdbc/execute-one! ds ["select count(*) as c
                            from reports
                            where ebot_match_id = ?" match-id]
                       {:builder-fn rs/as-unqualified-lower-maps}))
   0))
