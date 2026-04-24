App Work Flow :-

1)Constantly monitors for app notification (any app) with the full notification content
2)If the app detect any word which is present in a “Whitelisted_Words” (data class) the app automatically fades the system media volume from <current_volume> to 0 , Note save the current media volume count in “LMV”
3)After doing that , the package which is responsible for the trigger saves its package name in <last_media_app> . Set the “trigger” variable to true (trigger is boolean)
4)when the trigger is true , constantly check the notification for the particular <last_media_app> ,
5)If there is no longer the words (“Whitelisted_Words”) in there for the particular package then fade the system media volume from <0> to <LMV>
6)Again follow the same order from 1st point.
