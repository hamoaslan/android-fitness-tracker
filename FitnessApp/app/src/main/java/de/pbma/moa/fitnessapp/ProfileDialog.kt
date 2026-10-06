package com.example.pushupcounter

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment

class ProfileDialog(
    private val profileList: List<String>,
    private val onProfileSelected: (String?) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val profilesWithNew = profileList + "Neues Profil erstellen"
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Profil auswählen")

        builder.setItems(profilesWithNew.toTypedArray()) { _, which ->
            val selected = profilesWithNew[which]

            if (selected == "Neues Profil erstellen") {
                val input = EditText(requireContext())
                input.inputType = InputType.TYPE_CLASS_TEXT
                input.hint = "Profilname eingeben"

                AlertDialog.Builder(requireContext())
                    .setTitle("Neues Profil erstellen")
                    .setView(input)
                    .setPositiveButton("OK") { _, _ ->
                        val newName = input.text.toString().trim()
                        if (newName.isNotEmpty()) {
                            onProfileSelected(newName)  // neues Profil, Kalibrierung folgt
                        } else {
                            onProfileSelected(null) // fallback
                        }
                    }
                    .setNegativeButton("Abbrechen") { dialog, _ ->
                        dialog.dismiss()
                        onProfileSelected(null)
                    }
                    .show()
            } else {
                onProfileSelected(selected) // bestehendes Profil
            }
        }

        builder.setCancelable(false)
        return builder.create()
    }
}
