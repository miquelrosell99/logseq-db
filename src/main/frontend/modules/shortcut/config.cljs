(ns frontend.modules.shortcut.config
  (:require [clojure.data :as data]
            [clojure.string :as string]
            [electron.ipc :as ipc]
            [frontend.commands :as commands]
            [frontend.components.commit :as commit]
            [frontend.config :as config]
            [frontend.dicts :as dicts]
            [frontend.extensions.pdf.utils :as pdf-utils]
            [frontend.extensions.srs.handler :as srs]
            [frontend.handler.config :as config-handler]
            [frontend.handler.editor :as editor-handler]
            [frontend.handler.export :as export-handler]
            [frontend.handler.history :as history]
            [frontend.handler.journal :as journal-handler]
            [frontend.handler.jump :as jump-handler]
            [frontend.handler.notification :as notification]
            [frontend.handler.page :as page-handler]
            [frontend.handler.paste :as paste-handler]
            [frontend.handler.plugin :as plugin-handler]
            [frontend.handler.plugin-config :as plugin-config-handler]
            [frontend.handler.repo :as repo-handler]
            [frontend.handler.route :as route-handler]
            [frontend.handler.search :as search-handler]
            [frontend.handler.ui :as ui-handler]
            [frontend.handler.whiteboard :as whiteboard-handler]
            [frontend.handler.window :as window-handler]
            [frontend.modules.shortcut.before :as m]
            [frontend.state :as state]
            [frontend.util :refer [mac?] :as util]
            [medley.core :as medley]
            [promesa.core :as p]))

(defn- search
  [mode]
  (editor-handler/escape-editing {:select? true})
  (if (state/get-search-mode)
    (js/setTimeout #(route-handler/go-to-search! mode) 128)
    (route-handler/go-to-search! mode)))

;; TODO: Namespace all-default-keyboard-shortcuts keys with `:command` e.g.
;; `:command.date-picker/complete`. They are namespaced in translation but
;; almost everywhere else they are not which could cause needless conflicts
;; with other config keys

;; To add a new entry to this map, first add it here and then a description for
;; it under :commands keys of frontend.dicts.en/dicts
;; A shortcut is a map with the following keys:
;;  * :binding - A string representing a keybinding. Avoid using single letter
;;    shortcuts to allow chords that start with those characters
;;  * :fn - Fn or a qualified keyword that represents a fn
;;  * :inactive - Optional boolean to disable a shortcut for certain conditions
;;    e.g. a given platform or feature condition
;;  * :file-graph? - Optional boolean to identify a command to only be run in file graphs
;;    and warned gracefully in db graphs
(def ^:large-vars/data-var all-built-in-keyboard-shortcuts
  {:pdf/previous-page                       {:binding "alt+p"
                                             :fn      pdf-utils/prev-page}

   :pdf/next-page                           {:binding "alt+n"
                                             :fn      pdf-utils/next-page}

   :pdf/close                               {:binding "alt+x"
                                             :fn      #(state/set-state! :pdf/current nil)}

   :pdf/find                                {:binding "alt+f"
                                             :fn      pdf-utils/open-finder}

   :whiteboard/select                       {:binding ["1" "w s"]
                                             :fn      #(.selectTool ^js (state/active-tldraw-app) "select")}

   :whiteboard/pan                          {:binding ["2" "w p"]
                                             :fn      #(.selectTool ^js (state/active-tldraw-app) "move")}

   :whiteboard/portal                       {:binding ["3" "w b"]
                                             :fn      #(.selectTool ^js (state/active-tldraw-app) "logseq-portal")}

   :whiteboard/pencil                       {:binding ["4" "w d"]
                                             :fn      #(.selectTool ^js (state/active-tldraw-app) "pencil")}

   :whiteboard/highlighter                  {:binding ["5" "w h"]
                                             :fn      #(.selectTool ^js (state/active-tldraw-app) "highlighter")}

   :whiteboard/eraser                       {:binding ["6" "w e"]
                                             :fn      #(.selectTool ^js (state/active-tldraw-app) "erase")}

   :whiteboard/connector                    {:binding ["7" "w c"]
                                             :fn      #(.selectTool ^js (state/active-tldraw-app) "line")}

   :whiteboard/text                         {:binding ["8" "w t"]
                                             :fn      #(.selectTool ^js (state/active-tldraw-app) "text")}

   :whiteboard/rectangle                    {:binding ["9" "w r"]
                                             :fn      #(.selectTool ^js (state/active-tldraw-app) "box")}

   :whiteboard/ellipse                      {:binding ["o" "w o"]
                                             :fn      #(.selectTool ^js (state/active-tldraw-app) "ellipse")}

   :whiteboard/reset-zoom                   {:binding "shift+0"
                                             :fn      #(.resetZoom (.-api ^js (state/active-tldraw-app)))}

   :whiteboard/zoom-to-fit                  {:binding "shift+1"
                                             :fn      #(.zoomToFit (.-api ^js (state/active-tldraw-app)))}

   :whiteboard/zoom-to-selection            {:binding "shift+2"
                                             :fn      #(.zoomToSelection (.-api ^js (state/active-tldraw-app)))}

   :whiteboard/zoom-out                     {:binding "shift+dash"
                                             :fn      #(.zoomOut (.-api ^js (state/active-tldraw-app)) false)}

   :whiteboard/zoom-in                      {:binding "shift+equals"
                                             :fn      #(.zoomIn (.-api ^js (state/active-tldraw-app)) false)}

   :whiteboard/send-backward                {:binding "open-square-bracket"
                                             :fn      #(.sendBackward ^js (state/active-tldraw-app))}

   :whiteboard/send-to-back                 {:binding "shift+open-square-bracket"
                                             :fn      #(.sendToBack ^js (state/active-tldraw-app))}

   :whiteboard/bring-forward                {:binding "close-square-bracket"
                                             :fn      #(.bringForward ^js (state/active-tldraw-app))}

   :whiteboard/bring-to-front               {:binding "shift+close-square-bracket"
                                             :fn      #(.bringToFront ^js (state/active-tldraw-app))}

   :whiteboard/lock                         {:binding "mod+l"
                                             :fn      #(.setLocked ^js (state/active-tldraw-app) true)}

   :whiteboard/unlock                       {:binding "mod+shift+l"
                                             :fn      #(.setLocked ^js (state/active-tldraw-app) false)}

   :whiteboard/group                        {:binding "mod+g"
                                             :fn      #(.doGroup (.-api ^js (state/active-tldraw-app)))}

   :whiteboard/ungroup                      {:binding "mod+shift+g"
                                             :fn      #(.unGroup (.-api ^js (state/active-tldraw-app)))}

   :whiteboard/toggle-grid                  {:binding "t g"
                                             :fn      #(.toggleGrid (.-api ^js (state/active-tldraw-app)))}

   :whiteboard/clone-right                  {:binding (if mac? "ctrl+shift+right" "alt+right")
                                             :fn      #(.clone (.-api ^js (state/active-tldraw-app)) "right")}

   :whiteboard/clone-left                   {:binding (if mac? "ctrl+shift+left" "alt+left")
                                             :fn      #(.clone (.-api ^js (state/active-tldraw-app)) "left")}

   :whiteboard/clone-up                     {:binding (if mac? "ctrl+shift+up" "alt+up")
                                             :fn      #(.clone (.-api ^js (state/active-tldraw-app)) "up")}

   :whiteboard/clone-down                   {:binding (if mac? "ctrl+shift+down" "alt+down")
                                             :fn      #(.clone (.-api ^js (state/active-tldraw-app)) "down")}

   :auto-complete/complete                  {:binding "enter"
                                             :fn      ui-handler/auto-complete-complete}

   :auto-complete/prev                      {:binding ["up" "ctrl+p"]
                                             :fn      ui-handler/auto-complete-prev}

   :auto-complete/next                      {:binding ["down" "ctrl+n"]
                                             :fn      ui-handler/auto-complete-next}

   :auto-complete/shift-complete            {:binding "shift+enter"
                                             :fn      ui-handler/auto-complete-shift-complete}

   :auto-complete/meta-complete             {:binding "mod+enter"
                                             :fn      (fn [state e]
                                                        (ui-handler/auto-complete-complete state e))}

   :cards/toggle-answers                    {:binding "s"
                                             :fn      srs/toggle-answers}

   :cards/next-card                         {:binding "n"
                                             :fn      srs/next-card}

   :cards/forgotten                         {:binding "f"
                                             :fn      srs/forgotten}

   :cards/remembered                        {:binding "r"
                                             :fn      srs/remembered}

   :cards/recall                            {:binding "t"
                                             :fn      srs/recall}

   :cards/again                             {:binding "1"
                                             :fn      srs/card-again}

   :cards/hard                              {:binding "2"
                                             :fn      srs/card-hard}

   :cards/good                              {:binding "3"
                                             :fn      srs/card-good}

   :cards/easy                              {:binding "4"
                                             :fn      srs/card-easy}

   :editor/escape-editing                   {:binding []
                                             :fn      (fn [_ _]
                                                        (editor-handler/escape-editing))}

   :editor/backspace                        {:binding "backspace"
                                             :fn      editor-handler/editor-backspace}

   :editor/delete                           {:binding "delete"
                                             :fn      editor-handler/editor-delete}

   :editor/new-block                        {:binding "enter"
                                             :fn      editor-handler/keydown-new-block-handler}

   :editor/new-line                         {:binding "shift+enter"
                                             :fn      editor-handler/keydown-new-line-handler}

   :editor/new-whiteboard                   {:binding "n w"
                                             :fn      (fn []
                                                        (when-not (config/db-based-graph? (state/get-current-repo))
                                                          (whiteboard-handler/<create-new-whiteboard-and-redirect!)))}

   :editor/follow-link                      {:binding "mod+o"
                                             :fn      editor-handler/follow-link-under-cursor!}

   :editor/open-link-in-sidebar             {:binding "mod+shift+o"
                                             :fn      editor-handler/open-link-in-sidebar!}

   :editor/bold                             {:binding "mod+b"
                                             :fn      editor-handler/bold-format!}

   :editor/italics                          {:binding "mod+i"
                                             :fn      editor-handler/italics-format!}

   :editor/highlight                        {:binding "mod+shift+h"
                                             :fn      editor-handler/highlight-format!}

   :editor/strike-through                   {:binding "mod+shift+s"
                                             :fn      editor-handler/strike-through-format!}

   :editor/clear-block                      {:binding (if mac? "ctrl+l" "alt+l")
                                             :fn      editor-handler/clear-block-content!}

   :editor/kill-line-before                 {:binding (if mac? "ctrl+u" "alt+u")
                                             :fn      editor-handler/kill-line-before!}

   :editor/kill-line-after                  {:binding (if mac? false "alt+k")
                                             :fn      editor-handler/kill-line-after!}

   :editor/beginning-of-block               {:binding (if mac? false "alt+a")
                                             :fn      editor-handler/beginning-of-block}

   :editor/end-of-block                     {:binding (if mac? false "alt+e")
                                             :fn      editor-handler/end-of-block}

   :editor/forward-word                     {:binding (if mac? "ctrl+shift+f" "alt+f")
                                             :fn      editor-handler/cursor-forward-word}

   :editor/backward-word                    {:binding (if mac? "ctrl+shift+b" "alt+b")
                                             :fn      editor-handler/cursor-backward-word}

   :editor/forward-kill-word                {:binding (if mac? "ctrl+w" "alt+d")
                                             :fn      editor-handler/forward-kill-word}

   :editor/backward-kill-word               {:binding (if mac? false "alt+w")
                                             :fn      editor-handler/backward-kill-word}

   :editor/replace-block-reference-at-point {:binding "mod+shift+r"
                                             :fn      editor-handler/replace-block-reference-with-content-at-point}
   :editor/copy-embed                       {:binding "mod+shift+e"
                                             :fn      editor-handler/copy-current-block-embed}

   :editor/paste-text-in-one-block-at-point {:binding "mod+shift+v"
                                             :fn      paste-handler/editor-on-paste-raw!}

   :editor/insert-youtube-timestamp         {:binding "mod+shift+y"
                                             :fn      commands/insert-youtube-timestamp}

   :editor/cycle-todo                       {:binding "mod+enter"
                                             :fn      editor-handler/cycle-todo!}

   :editor/up                               {:binding ["up" "ctrl+p"]
                                             :fn      (editor-handler/shortcut-up-down :up)}

   :editor/down                             {:binding ["down" "ctrl+n"]
                                             :fn      (editor-handler/shortcut-up-down :down)}

   :editor/left                             {:binding "left"
                                             :fn      (editor-handler/shortcut-left-right :left)}

   :editor/right                            {:binding "right"
                                             :fn      (editor-handler/shortcut-left-right :right)}

   :editor/move-block-up                    {:binding (if mac? "mod+shift+up" "alt+shift+up")
                                             :fn      (editor-handler/move-up-down true)}

   :editor/move-block-down                  {:binding (if mac? "mod+shift+down" "alt+shift+down")
                                             :fn      (editor-handler/move-up-down false)}

   :editor/open-edit                        {:binding "enter"
                                             :fn      (fn [e]
                                                        (editor-handler/open-selected-block! :right e))}
   :editor/open-selected-blocks-in-sidebar  {:binding "shift+enter"
                                             :fn      editor-handler/open-selected-blocks-in-sidebar!}

   :editor/select-block-up                  {:binding "alt+up"
                                             :fn      (editor-handler/on-select-block :up)}

   :editor/select-block-down                {:binding "alt+down"
                                             :fn      (editor-handler/on-select-block :down)}

   :editor/select-up                        {:binding "shift+up"
                                             :fn      (editor-handler/shortcut-select-up-down :up)}

   :editor/select-down                      {:binding "shift+down"
                                             :fn      (editor-handler/shortcut-select-up-down :down)}

   :editor/delete-selection                 {:binding ["backspace" "delete"]
                                             :fn      editor-handler/delete-selection}

   :editor/expand-block-children            {:binding "mod+down"
                                             :fn      editor-handler/expand!}

   :editor/collapse-block-children          {:binding "mod+up"
                                             :fn      editor-handler/collapse!}

   :editor/toggle-block-children            {:binding "mod+;"
                                             :fn      editor-handler/toggle-collapse!}

   :editor/indent                           {:binding "tab"
                                             :fn      (editor-handler/keydown-tab-handler :right)}

   :editor/outdent                          {:binding "shift+tab"
                                             :fn      (editor-handler/keydown-tab-handler :left)}

   :editor/copy                             {:binding "mod+c"
                                             :fn      editor-handler/shortcut-copy}

   :editor/copy-text                        {:binding "mod+shift+c"
                                             :fn      editor-handler/shortcut-copy-text}

   :editor/cut                              {:binding "mod+x"
                                             :fn      editor-handler/shortcut-cut}

   :editor/undo                             {:binding "mod+z"
                                             :fn      history/undo!}

   :editor/redo                             {:binding ["mod+shift+z" "mod+y"]
                                             :fn      history/redo!}

   :editor/insert-link                      {:binding "mod+l"
                                             :fn      #(editor-handler/html-link-format!)}

   :editor/select-all-blocks                {:binding "mod+shift+a"
                                             :fn      editor-handler/select-all-blocks!}

   :editor/select-parent                    {:binding "mod+a"
                                             :fn      editor-handler/select-parent}

   :editor/zoom-in                          {:binding (if mac? ["mod+." "mod+shift+."] "alt+right") ; FIXME: mod+. not works on Chrome
                                             :fn      editor-handler/zoom-in!}

   :editor/zoom-out                         {:binding (if mac? "mod+," "alt+left")
                                             :fn      editor-handler/zoom-out!}

   :editor/toggle-number-list               {:binding "t n"
                                             :fn      #(state/pub-event! [:editor/toggle-own-number-list (state/get-selection-block-ids)])}

   :editor/add-property                     {:binding (if mac? "mod+p" "ctrl+alt+p")
                                             :db-graph? true
                                             :fn      (fn [e]
                                                        (when e (util/stop e))
                                                        (state/pub-event! [:editor/new-property {}]))}

   :editor/add-property-deadline            {:binding "p d"
                                             :db-graph? true
                                             :selection? true
                                             :fn      (fn []
                                                        (state/pub-event! [:editor/new-property {:property-key "Deadline"}]))}

   :editor/add-property-status              {:binding "p s"
                                             :db-graph? true
                                             :selection? true
                                             :fn      (fn []
                                                        (state/pub-event! [:editor/new-property {:property-key "Status"}]))}

   :editor/add-property-priority            {:binding "p p"
                                             :db-graph? true
                                             :selection? true
                                             :fn      (fn []
                                                        (state/pub-event! [:editor/new-property {:property-key "Priority"}]))}

   :editor/add-property-icon                {:binding "p i"
                                             :db-graph? true
                                             :selection? true
                                             :fn      (fn []
                                                        (state/pub-event! [:editor/new-property {:property-key "Icon"}]))}

   :editor/toggle-display-all-properties    {:binding "p t"
                                             :db-graph? true
                                             :fn      ui-handler/toggle-show-empty-hidden-properties!}

   :ui/toggle-brackets                      {:binding "t b"
                                             :fn      config-handler/toggle-ui-show-brackets!}

   :go/search                               {:binding "mod+k"
                                             :fn      #(search :global)}

   :go/search-themes                        {:binding "mod+shift+i"
                                             :fn      #(search :themes)}

   :command-palette/toggle                  {:binding "mod+shift+p"
                                             :fn      #(search :commands)}

   :go/search-in-page                       {:binding "mod+shift+k"
                                             :fn      #(search :current-page)}

   :go/electron-find-in-page                {:binding  "mod+f"
                                             :inactive (not (util/electron?))
                                             :fn       #(search-handler/open-find-in-page!)}

   :go/electron-jump-to-the-next            {:binding  ["enter" "mod+g"]
                                             :inactive (not (util/electron?))
                                             :fn       #(search-handler/loop-find-in-page! false)}

   :go/electron-jump-to-the-previous        {:binding  ["shift+enter" "mod+shift+g"]
                                             :inactive (not (util/electron?))
                                             :fn       #(search-handler/loop-find-in-page! true)}

   :go/journals                             {:binding "g j"
                                             :fn      route-handler/go-to-journals!}

   :go/backward                             {:binding "mod+open-square-bracket"
                                             :fn      (fn [_] (js/window.history.back))}

   :go/forward                              {:binding "mod+close-square-bracket"
                                             :fn      (fn [_] (js/window.history.forward))}

   :search/re-index                         {:binding "mod+c mod+s"
                                             :fn      (fn [_] (search-handler/rebuild-indices! true))}

   :sidebar/open-today-page                 {:binding (if mac? "mod+shift+j" "alt+shift+j")
                                             :fn      page-handler/open-today-in-sidebar}

   :sidebar/close-top                       {:binding "c t"
                                             :fn      #(state/sidebar-remove-block! 0)}

   :sidebar/clear                           {:binding "mod+c mod+c"
                                             :fn      #(do
                                                         (state/clear-sidebar-blocks!)
                                                         (state/hide-right-sidebar!))}

   :misc/copy                               {:binding "mod+c"
                                             :fn      (fn [] (js/document.execCommand "copy"))}

   :graph/export-as-html                    {:fn      #(export-handler/download-repo-as-html!
                                                        (state/get-current-repo))
                                             :binding []}

   :graph/open                              {:fn      #(do
                                                         (editor-handler/escape-editing)
                                                         (state/pub-event! [:dialog-select/graph-open]))
                                             :binding "alt+shift+g"}

   :graph/remove                            {:fn      #(do
                                                         (editor-handler/escape-editing)
                                                         (state/pub-event! [:dialog-select/graph-remove]))
                                             :binding []}

   :graph/add                               {:fn      (fn [] (route-handler/redirect! {:to :graphs}))
                                             :binding []}

   :graph/db-add                            {:fn #(state/pub-event! [:graph/new-db-graph])
                                             :binding false}

   :graph/db-save                           {:fn #(state/pub-event! [:graph/save-db-to-disk])
                                             :inactive (not (util/electron?))
                                             :binding "mod+s"}

   :graph/re-index                          {:fn      (fn []
                                                        (p/let [multiple-windows? (ipc/ipc "graphHasMultipleWindows" (state/get-current-repo))]
                                                          (state/pub-event! [:graph/ask-for-re-index (atom multiple-windows?) nil])))
                                             :file-graph? true
                                             :binding []}

   :command/run                             {:binding  "mod+shift+1"
                                             :inactive (not (util/electron?))
                                             :fn       #(do
                                                          (editor-handler/escape-editing {:select? true})
                                                          (state/pub-event! [:command/run]))}

   :go/home                                 {:binding "g h"
                                             :fn      #(route-handler/redirect-to-home!)}

   :go/all-pages                            {:binding "g a"
                                             :fn      route-handler/redirect-to-all-pages!}

   :go/graph-view                           {:binding "g g"
                                             :fn      route-handler/redirect-to-graph-view!}

   :go/all-graphs                           {:binding "g shift+g"
                                             :fn      route-handler/redirect-to-all-graphs}

   :go/whiteboards                          {:binding "g w"
                                             :fn      route-handler/redirect-to-whiteboard-dashboard!}

   :go/keyboard-shortcuts                   {:binding "g s"
                                             :fn      #(state/pub-event! [:modal/keymap])}

   :go/tomorrow                             {:binding "g t"
                                             :fn      journal-handler/go-to-tomorrow!}

   :go/next-journal                         {:binding "g n"
                                             :fn      journal-handler/go-to-next-journal!}

   :go/prev-journal                         {:binding "g p"
                                             :fn      journal-handler/go-to-prev-journal!}

   :go/flashcards                           {:binding ["g f" "t c"]
                                             :fn      ui-handler/toggle-cards!}

   :ui/toggle-document-mode                 {:binding "t d"
                                             :fn      state/toggle-document-mode!}

   :ui/highlight-recent-blocks              {:fn      state/toggle-highlight-recent-blocks!}

   :ui/toggle-settings                      {:binding (if mac? ["t s" "mod+,"] "t s")
                                             :fn      ui-handler/toggle-settings-modal!}

   :ui/toggle-right-sidebar                 {:binding "t r"
                                             :fn      ui-handler/toggle-right-sidebar!}

   :ui/toggle-left-sidebar                  {:binding "t l"
                                             :fn      state/toggle-left-sidebar!}

   :ui/toggle-help                          {:binding "shift+/"
                                             :fn      ui-handler/toggle-help!}

   :ui/toggle-theme                         {:binding "t t"
                                             :fn      state/toggle-theme!}

   :ui/toggle-contents                      {:binding "alt+shift+c"
                                             :fn      ui-handler/toggle-contents!}

   :command/toggle-favorite                 {:binding "mod+shift+f"
                                             :fn      page-handler/toggle-favorite!}

   :editor/quick-add                        {:binding (if mac? "mod+e" "mod+alt+e")
                                             :db-graph? true
                                             :fn      editor-handler/quick-add}
   :editor/jump                             {:binding "mod+j"
                                             :fn      jump-handler/jump-to}
   :editor/open-file-in-default-app         {:binding  "mod+d mod+a"
                                             :inactive (not (util/electron?))
                                             :file-graph? true
                                             :fn       page-handler/open-file-in-default-app}

   :editor/open-file-in-directory           {:binding  "mod+d mod+i"
                                             :inactive (not (util/electron?))
                                             :file-graph? true
                                             :fn       page-handler/open-file-in-directory}

   :editor/copy-current-file                {:binding  false
                                             :inactive (not (util/electron?))
                                             :file-graph? true
                                             :fn       page-handler/copy-current-file}

   :editor/copy-page-url                    {:binding  []
                                             :inactive (not (util/electron?))
                                             :fn       #(page-handler/copy-page-url)}

   :window/close                            {:binding  "mod+w"
                                             :inactive (not (util/electron?))
                                             :fn       window-handler/close!}

   :ui/toggle-wide-mode                     {:binding "t w"
                                             :fn      ui-handler/toggle-wide-mode!}

   :ui/select-theme-color                   {:binding "t i"
                                             :fn      #(plugin-handler/show-themes-modal! true)}

   :ui/goto-plugins                         {:binding  "t p"
                                             :inactive (not config/lsp-enabled?)
                                             :fn       plugin-handler/goto-plugins-dashboard!}

   :ui/install-plugins-from-file            {:binding  []
                                             :inactive (not (config/plugin-config-enabled?))
                                             :fn       plugin-config-handler/open-replace-plugins-modal}

   :ui/install-plugin-from-github           {:binding  []
                                             :inactive (or (not config/lsp-enabled?) (not (util/electron?)))
                                             :fn       plugin-config-handler/open-install-plugin-from-github-modal}

   :ui/clear-all-notifications              {:binding []
                                             :fn      :frontend.handler.notification/clear-all!}

   :editor/toggle-open-blocks               {:binding "t o"
                                             :fn      editor-handler/toggle-open!}

   :ui/customize-appearance                 {:binding "c c"
                                             :fn      #(state/pub-event! [:ui/toggle-appearance])}

   :git/commit {:binding "mod+g c"
                :inactive (not (util/electron?))
                :fn commit/show-commit-modal!}

   :dev/fix-broken-graph {:binding []
                          :db-graph? true
                          :fn #(repo-handler/fix-broken-graph! (state/get-current-repo))}

   :dev/gc-graph {:binding []
                  :inactive (not (state/developer-mode?))
                  :fn #(repo-handler/gc-graph! (state/get-current-repo))}

   :dev/replace-graph-with-db-file {:binding []
                                    :inactive (or (not (util/electron?)) (not (state/developer-mode?)))
                                    :fn :frontend.handler.common.developer/replace-graph-with-db-file}

   :dev/show-block-data {:binding []
                         :inactive (not (state/developer-mode?))
                         :fn :frontend.handler.common.developer/show-block-data}

   :dev/show-block-ast {:binding []
                        :inactive (not (state/developer-mode?))
                        :fn :frontend.handler.common.developer/show-block-ast}

   :dev/show-page-data {:binding []
                        :inactive (not (state/developer-mode?))
                        :fn :frontend.handler.common.developer/show-page-data}

   :dev/show-page-ast {:binding []
                       :inactive (not (state/developer-mode?))
                       :fn :frontend.handler.common.developer/show-page-ast}

   :misc/export-block-data {:binding []
                            :db-graph? true
                            :fn :frontend.handler.db-based.export/export-block-data}

   :misc/export-page-data {:binding []
                           :db-graph? true
                           :fn :frontend.handler.db-based.export/export-page-data}

   :misc/export-graph-ontology-data {:binding []
                                     :db-graph? true
                                     :fn :frontend.handler.db-based.export/export-graph-ontology-data}

   :misc/import-edn-data {:binding []
                          :db-graph? true
                          :fn :frontend.handler.db-based.import/import-edn-data-dialog}

   :dev/validate-db   {:binding []
                       :db-graph? true
                       :inactive (not (state/developer-mode?))
                       :fn :frontend.handler.common.developer/validate-db}
   :dev/rtc-stop {:binding []
                  :db-graph? true
                  :inactive (not (state/developer-mode?))
                  :fn :frontend.handler.common.developer/rtc-stop}
   :dev/rtc-start {:binding []
                   :db-graph? true
                   :inactive (not (state/developer-mode?))
                   :fn :frontend.handler.common.developer/rtc-start}})

(let [keyboard-commands
      {::commands (set (keys all-built-in-keyboard-shortcuts))
       ::dicts/commands dicts/abbreviated-commands}]
  (assert (= (::commands keyboard-commands) (::dicts/commands keyboard-commands))
          (str "Keyboard commands must have an english label"
               (data/diff (::commands keyboard-commands) (::commands keyboard-commands)))))

(defn- resolve-fn
  "Converts a keyword fn to the actual fn. The fn to be resolved needs to be
  marked as ^:export for advanced mode"
  [keyword-fn]
  (fn []
    (if-let [resolved-fn (some-> (namespace keyword-fn)
                                 ;; handle reserved words
                                 (string/replace-first ".export" ".export$")
                                 (string/replace-first ".import" ".import$")
                                 find-ns-obj
                                 (aget (munge (name keyword-fn))))]
      (resolved-fn)
      (throw (ex-info (str "Unable to resolve " keyword-fn " to a fn") {})))))

(defn- wrap-fn-with-db-graph-only-warning
  "Wraps DB graph only commands so they are only run in DB graphs and warned
   when in file graphs"
  [f]
  (fn []
    (if (config/db-based-graph? (state/get-current-repo))
      (f)
      (notification/show! "This command is only for DB graphs." :warning true nil 3000))))

(defn- wrap-fn-with-file-graph-only-warning
  "Wraps file graph only commands so they are only run in file graphs and warned
   when in DB graphs"
  [f]
  (fn []
    (if (config/db-based-graph? (state/get-current-repo))
      (notification/show! "This command is only for file graphs." :warning true nil 3000)
      (f))))

(defn build-category-map [ks]
  (->> (if (sequential? ks)
         ks (let [{:keys [ns includes excludes]} ks]
              (->> (keys all-built-in-keyboard-shortcuts)
                   (filter (fn [k]
                             (and (or (and ns (keyword? k)
                                           (contains? (->> (if (seqable? ns) (seq ns) [ns]) (map #(name %)) (set))
                                                      (namespace k)))
                                      (and includes (contains? (set includes) k)))
                                  (if (not (seq excludes)) true (not (contains? (set excludes) k)))))))))
       (select-keys all-built-in-keyboard-shortcuts)
       (remove (comp :inactive val))
    ;; Convert keyword fns to real fns
       (map (fn [[k v]]
              [k (if (keyword? (:fn v))
                   (assoc v :fn (resolve-fn (:fn v)))
                   v)]))
       (map (fn [[k v]]
              [k (cond (:file-graph? v)
                       (update v :fn wrap-fn-with-file-graph-only-warning)
                       (:db-graph? v)
                       (update v :fn wrap-fn-with-db-graph-only-warning)
                       :else
                       v)]))
       (into {})))

;; This is the only var that should be publicly expose :fn functionality
(defonce ^:large-vars/data-var *config
  (atom
   {:shortcut.handler/date-picker
    (build-category-map {:ns :date-picker})

    :shortcut.handler/pdf
    (-> (build-category-map {:ns :pdf})
        (with-meta {:before m/enable-when-not-editing-mode!}))

    :shortcut.handler/whiteboard
    (-> (build-category-map {:ns :whiteboard})
        (with-meta {:before m/enable-when-not-editing-mode!}))

    :shortcut.handler/auto-complete
    (build-category-map {:ns :auto-complete})

    :shortcut.handler/cards
    (-> (build-category-map {:ns :cards})
        (with-meta {:before m/enable-when-not-editing-mode!}))

    :shortcut.handler/block-editing-only
    (-> (build-category-map
         [:editor/escape-editing
          :editor/backspace
          :editor/delete
          :editor/zoom-in
          :editor/zoom-out
          :editor/new-block
          :editor/new-line
          :editor/follow-link
          :editor/open-link-in-sidebar
          :editor/bold
          :editor/italics
          :editor/highlight
          :editor/strike-through
          :editor/clear-block
          :editor/kill-line-before
          :editor/kill-line-after
          :editor/beginning-of-block
          :editor/end-of-block
          :editor/forward-word
          :editor/backward-word
          :editor/forward-kill-word
          :editor/backward-kill-word
          :editor/replace-block-reference-at-point
          :editor/copy-embed
          :editor/paste-text-in-one-block-at-point
          :editor/insert-youtube-timestamp])
        (with-meta {:before m/enable-when-editing-mode!}))

    :shortcut.handler/editor-global
    (-> (build-category-map
         [:graph/export-as-html
          :graph/open
          :graph/remove
          :graph/add
          :graph/db-add
          :graph/db-save
          :graph/re-index
          :editor/cycle-todo
          :editor/up
          :editor/down
          :editor/left
          :editor/right
          :editor/select-up
          :editor/select-down
          :editor/move-block-up
          :editor/move-block-down
          :editor/open-edit
          :editor/open-selected-blocks-in-sidebar
          :editor/select-block-up
          :editor/select-block-down
          :editor/select-parent
          :editor/delete-selection
          :editor/expand-block-children
          :editor/collapse-block-children
          :editor/toggle-block-children
          :editor/indent
          :editor/outdent
          :editor/copy
          :editor/copy-text
          :editor/cut
          :command/toggle-favorite
          :editor/jump])
        (with-meta {:before m/enable-when-not-component-editing!}))

    :shortcut.handler/global-prevent-default
    (-> (build-category-map
         [:editor/insert-link
          :editor/select-all-blocks
          :editor/toggle-number-list
          :editor/undo
          :editor/redo
          :editor/quick-add
          :ui/toggle-brackets
          :go/search-in-page
          :go/search
          :go/search-themes
          :go/electron-find-in-page
          :go/electron-jump-to-the-next
          :go/electron-jump-to-the-previous
          :go/backward
          :go/forward
          :search/re-index
          :sidebar/open-today-page
          :sidebar/clear
          :command/run
          :command-palette/toggle
          :editor/add-property
          :window/close])
        (with-meta {:before m/prevent-default-behavior}))

    :shortcut.handler/global-non-editing-only
    (-> (build-category-map
         [:go/home
          :go/journals
          :go/all-pages
          :go/flashcards
          :go/graph-view
          :go/all-graphs
          :go/whiteboards
          :go/keyboard-shortcuts
          :go/tomorrow
          :go/next-journal
          :go/prev-journal
          :ui/toggle-document-mode
          :ui/highlight-recent-blocks
          :ui/toggle-settings
          :ui/toggle-right-sidebar
          :ui/toggle-left-sidebar
          :ui/toggle-help
          :ui/toggle-theme
          :ui/toggle-contents
          :editor/open-file-in-default-app
          :editor/open-file-in-directory
          :editor/copy-current-file
          :editor/copy-page-url
          :editor/new-whiteboard
          :editor/add-property-deadline
          :editor/add-property-status
          :editor/add-property-priority
          :editor/add-property-icon
          :editor/toggle-display-all-properties
          :ui/toggle-wide-mode
          :ui/select-theme-color
          :ui/goto-plugins
          :ui/install-plugins-from-file
          :ui/install-plugin-from-github
          :editor/toggle-open-blocks
          :ui/clear-all-notifications
          :git/commit
          :sidebar/close-top
          :misc/export-block-data
          :misc/export-page-data
          :misc/export-graph-ontology-data
          :misc/import-edn-data
          :dev/show-block-data
          :dev/show-block-ast
          :dev/show-page-data
          :dev/show-page-ast
          :dev/replace-graph-with-db-file
          :dev/validate-db
          :dev/fix-broken-graph
          :dev/gc-graph
          :dev/rtc-stop
          :dev/rtc-start
          :ui/customize-appearance])
        (with-meta {:before m/enable-when-not-editing-mode!}))

    :shortcut.handler/misc
     ;; always overrides the copy due to "mod+c mod+s"
    {:misc/copy (:misc/copy all-built-in-keyboard-shortcuts)}}))

;; To add a new entry to this map, first add it here and then
;; a description for it in frontend.dicts.en/dicts
;; Full list of categories for docs purpose
(defonce ^:large-vars/data-var *category
  (atom
   {:shortcut.category/basics
    [:go/search
     :go/search-themes
     :editor/new-block
     :editor/new-line
     :editor/indent
     :editor/outdent
     :editor/select-all-blocks
     :editor/select-parent
     :go/search-in-page
     :command-palette/toggle
     :go/electron-find-in-page
     :go/electron-jump-to-the-next
     :go/electron-jump-to-the-previous
     :editor/undo
     :editor/redo
     :editor/copy
     :editor/copy-text
     :editor/cut
     :editor/quick-add]

    :shortcut.category/formatting
    [:editor/bold
     :editor/insert-link
     :editor/italics
     :editor/strike-through
     :editor/highlight]

    :shortcut.category/navigating
    [:editor/up
     :editor/down
     :editor/left
     :editor/right
     :editor/collapse-block-children
     :editor/expand-block-children
     :editor/toggle-block-children
     :editor/toggle-open-blocks
     :editor/jump
     :go/backward
     :go/forward
     :go/home
     :go/journals
     :go/all-pages
     :go/graph-view
     :go/all-graphs
     :go/whiteboards
     :go/flashcards
     :go/tomorrow
     :go/next-journal
     :go/prev-journal
     :go/keyboard-shortcuts]

    :shortcut.category/block-editing
    [:editor/backspace
     :editor/delete
     :editor/indent
     :editor/outdent
     :editor/new-block
     :editor/new-line
     :editor/zoom-in
     :editor/zoom-out
     :editor/cycle-todo
     :editor/follow-link
     :editor/open-link-in-sidebar
     :editor/move-block-up
     :editor/move-block-down
     :editor/escape-editing]

    :shortcut.category/block-command-editing
    [:editor/backspace
     :editor/clear-block
     :editor/kill-line-before
     :editor/kill-line-after
     :editor/beginning-of-block
     :editor/end-of-block
     :editor/forward-word
     :editor/backward-word
     :editor/forward-kill-word
     :editor/backward-kill-word
     :editor/replace-block-reference-at-point
     :editor/copy-embed
     :editor/paste-text-in-one-block-at-point
     :editor/select-up
     :editor/select-down]

    :shortcut.category/block-selection
    [:editor/open-edit
     :editor/open-selected-blocks-in-sidebar
     :editor/select-all-blocks
     :editor/select-parent
     :editor/select-block-up
     :editor/select-block-down
     :editor/delete-selection
     :editor/add-property
     :editor/add-property-deadline
     :editor/add-property-status
     :editor/add-property-priority
     :editor/add-property-icon
     :editor/toggle-display-all-properties]

    :shortcut.category/toggle
    [:ui/toggle-help
     :editor/toggle-open-blocks
     :editor/toggle-number-list
     :ui/toggle-wide-mode
     :ui/highlight-recent-blocks
     :ui/toggle-brackets
     :ui/toggle-theme
     :ui/toggle-left-sidebar
     :ui/toggle-right-sidebar
     :ui/toggle-settings
     :ui/toggle-contents
     :ui/customize-appearance]

    :shortcut.category/whiteboard
    [:editor/new-whiteboard
     :whiteboard/select
     :whiteboard/pan
     :whiteboard/portal
     :whiteboard/pencil
     :whiteboard/highlighter
     :whiteboard/eraser
     :whiteboard/connector
     :whiteboard/text
     :whiteboard/rectangle
     :whiteboard/ellipse
     :whiteboard/reset-zoom
     :whiteboard/zoom-to-fit
     :whiteboard/zoom-to-selection
     :whiteboard/zoom-out
     :whiteboard/zoom-in
     :whiteboard/send-backward
     :whiteboard/send-to-back
     :whiteboard/bring-forward
     :whiteboard/bring-to-front
     :whiteboard/lock
     :whiteboard/unlock
     :whiteboard/group
     :whiteboard/ungroup
     :whiteboard/toggle-grid
     :whiteboard/clone-left
     :whiteboard/clone-right
     :whiteboard/clone-top
     :whiteboard/clone-bottom]

    :shortcut.category/others
    [:pdf/previous-page
     :pdf/next-page
     :pdf/close
     :pdf/find
     :command/toggle-favorite
     :command/run
     :graph/export-as-html
     :graph/open
     :graph/remove
     :graph/add
     :graph/re-index
     :sidebar/close-top
     :sidebar/clear
     :sidebar/open-today-page
     :search/re-index
     :editor/insert-youtube-timestamp
     :editor/open-file-in-default-app
     :editor/open-file-in-directory
     :editor/copy-page-url
     :window/close
     :auto-complete/prev
     :auto-complete/next
     :auto-complete/complete
     :auto-complete/shift-complete
     :auto-complete/meta-complete
     :git/commit
     :misc/export-block-data
     :misc/export-page-data
     :misc/export-graph-ontology-data
     :misc/import-edn-data
     :dev/show-block-data
     :dev/show-block-ast
     :dev/show-page-data
     :dev/show-page-ast
     :dev/replace-graph-with-db-file
     :dev/validate-db
     :dev/fix-broken-graph
     :dev/gc-graph
     :dev/rtc-stop
     :dev/rtc-start
     :ui/clear-all-notifications]

    :shortcut.category/plugins
    []}))

(let [category-maps {::category (set (keys @*category))
                     ::dicts/category dicts/categories}]
  (assert (= (::category category-maps) (::dicts/category category-maps))
          (str "Keys for category maps must have an english label "
               (data/diff (::category category-maps) (::dicts/category category-maps)))))

(defn get-category-shortcuts
  "Active list of categories for docs purpose"
  [name]
  (get @*category name))

(def *shortcut-cmds (atom {}))

(defn add-shortcut!
  ([handler-id id shortcut-map] (add-shortcut! handler-id id shortcut-map false))
  ([handler-id id shortcut-map config-only?]
   (swap! *config assoc-in [handler-id id] shortcut-map)
   (when-not config-only?
     (swap! *shortcut-cmds assoc id (:cmd shortcut-map))
     (let [plugin? (string/starts-with? (str id) ":plugin.")
           category (or (:category shortcut-map)
                        (if plugin?
                          :shortcut.category/plugins
                          :shortcut.category/others))]
       (swap! *category update category #(conj % id))))))

(defn remove-shortcut!
  [handler-id id]
  (swap! *config medley/dissoc-in [handler-id id])
  (swap! *shortcut-cmds dissoc id)
  (doseq [category (keys @*category)]
    (swap! *category update category (fn [ids] (remove #(= % id) ids)))))
