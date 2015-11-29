# Assent

Assent is designed to make Marshmallow's runtime permissions easier to use. 

--

# Basics

The first way to use this library is to have Activities which request permissions extend `AssentActivity`.
AssentActivity will handle some dirty work internally, so all that you have to do is use the `requestPermissions` method:

```java
public class MainActivity extends AssentActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Assent.requestPermissions(new AssentCallback() {
            @Override
            public void onPermissionResult(PermissionResultSet result) {
                // Permission granted or denied
            }
        }, 69, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
}
```

`requestPermissions` has 3 parameters: a callback, a request code, and a list of permissions. You
can pass multiple permissions in your request like this:

```java
Assent.requestPermissions(callback, requestCode,
    Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION);
```

---

# Without AssentActivity

If you don't want to extend `AssentActivity`, you can use some of Assent's other methods in order to
mimic the behavior:

```java
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Updates the activity when the Activity is first created
        // That way you can request permissions from within onCreate()
        Assent.setActivity(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Updates the activity every time the Activity becomes visible again
        Assent.setActivity(this);
    }

    @Override
    protected void onStop() {
        // Cleans up references of the Activity to avoid memory leaks
        Assent.setActivity(null);
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Lets Assent handle permission results and contact your callbacks
        Assent.handleResult(permissions, grantResults);
    }
}
```

---

# Fragments

A huge plus to using callbacks rather than relying on `onRequestPermissionsResult` is that you
 can request permission from Fragments and receive the result right in the Fragment, as long as
 the Activity your Fragment is in handles results.
 
```java
public class MainFragment extends Fragment {

    // ... view creation logic and other stuff here
    
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        Assent.requestPermissions(new AssentCallback() {
            @Override
            public void onPermissionResult(PermissionResultSet result) {
                // Permission granted or denied
            }
        }, 69, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
}
```

---

# [LICENSE](/LICENSE.md)

###### Copyright 2016 Aidan Follestad

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.