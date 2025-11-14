package just.edu.bd.agrodss.pro;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private NavigationView navigationView;
    private androidx.drawerlayout.widget.DrawerLayout drawerLayout;
    //private TextView toolbarTitle;
    private long backPressedTime = 0;
    private static final int BACK_PRESS_DELAY = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupNavigation();

        // Load default fragment only if no saved state
        if (savedInstanceState == null) {
            Log.d(TAG, "Loading default Home fragment");
            // Use a simple transaction for initial load
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, new Home());
            transaction.commit();
            //toolbarTitle.setText("Home");
        }
    }

    private void initializeViews() {
        navigationView = findViewById(R.id.navigationView);
        drawerLayout = findViewById(R.id.main);
        ImageView menuIcon = findViewById(R.id.menuIcon);
        //toolbarTitle = findViewById(R.id.toolbarTitle);

        menuIcon.setOnClickListener(v -> {
            toggleDrawer();
        });
    }

    private void setupNavigation() {
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.home) {
                loadFragment(new Home(), "Home");
            } else if (itemId == R.id.dataset) {
                loadFragment(new Dataset(), "Datasets");
            } else if (itemId == R.id.publication) {
                loadFragment(new Publication(), "Publications");
            } else if (itemId == R.id.recommendation) {
                Log.d(TAG, "Loading Recommendation fragment");
                loadFragment(new Recommendation(), "Recommendations");
            } else if (itemId == R.id.about) {
                loadFragment(new About(), "About");
            } else if (itemId == R.id.web) {
                openWebsite();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    public void loadFragment(Fragment fragment, String title) {
        try {

            // Update title immediately
            //toolbarTitle.setText(title);

            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();

            // Only use animations for non-initial transactions
            if (fragmentManager.getBackStackEntryCount() > 0) {
                transaction.setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                );
            }

            transaction.replace(R.id.fragment_container, fragment);

            // Only add to backstack if it's NOT the Home fragment and NOT the initial load
            boolean isHomeFragment = fragment instanceof Home;
            boolean isInitialLoad = fragmentManager.getBackStackEntryCount() == 0 && isHomeFragment;

            if (!isInitialLoad && !isHomeFragment) {
                transaction.addToBackStack(null);
            } else {
                Log.d(TAG, "Not added to backstack: " + title + " (Home or initial load)");
            }

            transaction.commit();
            fragmentManager.executePendingTransactions();

            // Close drawer
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }

        } catch (Exception e) {
            showSnackbar("Error loading " + title);
        }
    }

    private void toggleDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    private void openWebsite() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://agrodss.com"));
            startActivity(intent);
        } catch (Exception e) {
            showSnackbar("No browser app found");
        }
    }

    private void updateToolbarTitle() {
        Fragment currentFragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);

        if (currentFragment == null) {
            //toolbarTitle.setText("AgroDSS");
            return;
        }

        String title = "AgroDSS";
        if (currentFragment instanceof Home) title = "Home";
        else if (currentFragment instanceof Dataset) title = "Datasets";
        else if (currentFragment instanceof Publication) title = "Publications";
        else if (currentFragment instanceof Recommendation) title = "Recommendations";
        else if (currentFragment instanceof About) title = "About";

        //toolbarTitle.setText(title);
        Log.d(TAG, "Toolbar title updated to: " + title);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();

        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
            // Update title after backstack pop
            getSupportFragmentManager().executePendingTransactions();
            updateToolbarTitle();
        } else {
            if (System.currentTimeMillis() - backPressedTime < BACK_PRESS_DELAY) {
                super.onBackPressed();
            } else {
                backPressedTime = System.currentTimeMillis();
                showSnackbar("Press back again to exit");
            }
        }
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }
}