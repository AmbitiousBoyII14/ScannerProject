package com.example.live

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Opt-in local SSH draft, not a trusted profile. Normal SSH pin checks still run on Connect. */
internal class RememberedSsh(context:Context){
    private val file=AtomicFile(File(context.noBackupFilesDir,"remembered-ssh.v1"))
    private val alias="scanner.ssh.draft.v1"
    private fun store()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    private fun key(create:Boolean):SecretKey {
        (store().getKey(alias,null) as? SecretKey)?.let{return it};check(create)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(raw:String){require(Profile.read(raw,forDiscovery=true).isSsh);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(true));check(c.iv.size==12);val bytes=c.iv+c.doFinal(raw.toByteArray());val out=file.startWrite();try{out.write(bytes);file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}}
    @Synchronized fun load():String {if(!file.baseFile.exists())return "";val bytes=file.readFully();require(bytes.size in 28..131100);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(false),GCMParameterSpec(128,bytes,0,12));return String(c.doFinal(bytes,12,bytes.size-12),Charsets.UTF_8)}
    @Synchronized fun forget(){file.delete();store().let{if(it.containsAlias(alias))it.deleteEntry(alias)}}
}
